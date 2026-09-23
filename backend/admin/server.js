/*
 * AlkilApp Admin Panel - Modern API Server
 * Serves static frontend + REST API for admin operations
 */
const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { Firestore } = require('@google-cloud/firestore');
const { expirarDestacados } = require('./expirar');

const PROJECT = 'gen-lang-client-0040505884';
const DATABASE = 'alkilappdb';
const SA = process.env.GOOGLE_APPLICATION_CREDENTIALS ||
    path.join(__dirname, '..', 'credentials', 'alkilapp-seed-sa.json');
const PASSWORD = (process.env.ADMIN_PASSWORD || '').trim();
const USER = (process.env.ADMIN_USER || '').trim();
const SECRET = process.env.ADMIN_SECRET || 'alkilapp-admin-dev';
const PORT = Number(process.env.PORT || 8080);

const opcionesDb = { projectId: PROJECT, databaseId: DATABASE };
if (fs.existsSync(SA)) opcionesDb.keyFilename = SA;
const db = new Firestore(opcionesDb);

const PUBLIC_DIR = path.join(__dirname, 'public');
const MIME_TYPES = {
    '.html': 'text/html; charset=utf-8',
    '.css': 'text/css; charset=utf-8',
    '.js': 'application/javascript; charset=utf-8',
    '.mjs': 'application/javascript; charset=utf-8',
    '.json': 'application/json; charset=utf-8',
    '.png': 'image/png',
    '.jpg': 'image/jpeg',
    '.jpeg': 'image/jpeg',
    '.svg': 'image/svg+xml',
    '.ico': 'image/x-icon',
    '.woff': 'font/woff',
    '.woff2': 'font/woff2',
};

// ---- Utils ----
const esc = (s) => String(s == null ? '' : s).replace(/[&<>"']/g, (c) => ({
    '&': '&',
    '<': '<',
    '>': '>',
    '"': '"',
    "'": "'"
}[c]));
const fecha = (v) => {
    if (!v) return '-';
    const d = typeof v.toDate === 'function' ? v.toDate() : new Date(v);
    return isNaN(d) ? '-' : d.toISOString().slice(0, 16).replace('T', ' ');
};
const mostrarCreado = (doc) => fecha(doc.createdAt || doc._creado);
const ponerCreado = (d) => ({ id: d.id, ...d.data(), _creado: d.createTime ? d.createTime.toDate() : null });
const firmar = (t) => crypto.createHmac('sha256', SECRET).update(t).digest('hex');
const SESION_TTL = 24 * 60 * 60 * 1000;

function cookies(req) {
    const out = {};
    (req.headers.cookie || '').split(';').forEach((p) => {
        const i = p.indexOf('=');
        if (i > 0) out[p.slice(0, i).trim()] = decodeURIComponent(p.slice(i + 1).trim());
    });
    return out;
}

function autenticado(req) {
    const token = cookies(req).alkil_admin;
    if (!token) return false;
    const [payload, firma] = token.split('.');
    if (!payload || !firma) return false;
    if (firma !== firmar(payload)) return false;
    const [id, ts] = payload.split('|');
    if (!id || !ts) return false;
    const ahora = Date.now();
    if (ahora - Number(ts) > SESION_TTL) return false;
    return true;
}

function leerCuerpo(req) {
    return new Promise((resolve) => {
        let data = '';
        req.on('data', (c) => { data += c; if (data.length > 5_000_000) req.destroy(); });
        req.on('end', () => resolve(new URLSearchParams(data)));
    });
}

// ---- Static file server ----
async function serveStatic(req, res, filePath) {
    const ext = path.extname(filePath).toLowerCase();
    const contentType = MIME_TYPES[ext] || 'application/octet-stream';
    try {
        const stat = fs.statSync(filePath);
        if (stat.isDirectory()) {
            const indexPath = path.join(filePath, 'index.html');
            if (fs.existsSync(indexPath)) {
                return serveStatic(req, res, indexPath);
            }
        }
        // Cache: long for assets, short for HTML/JS modules
        const isAsset = ['.png', '.jpg', '.jpeg', '.svg', '.ico', '.woff', '.woff2', '.css'].includes(ext);
        const isModule = ['.js', '.mjs'].includes(ext);
        const cacheControl = isAsset ? 'public, max-age=31536000, immutable' : (isModule ? 'public, max-age=0, must-revalidate' : 'no-store');
        res.writeHead(200, { 'Content-Type': contentType, 'Cache-Control': cacheControl });
        fs.createReadStream(filePath).pipe(res);
    } catch (e) {
        res.writeHead(404, { 'Content-Type': 'text/plain' });
        res.end('Not found');
    }
}

// ---- API Handlers ----
async function handleAPI(req, res, url) {
    const ruta = url.pathname;

    // CORS headers
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET,POST,PUT,DELETE,OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

    if (req.method === 'OPTIONS') {
        res.writeHead(204); return res.end();
    }

    try {
        // Cron (no auth)
        if (ruta === '/api/cron/expirar') {
            if (!SECRET || url.searchParams.get('clave') !== SECRET) {
                return json(res, 403, { error: 'clave invalida' });
            }
            const r = await expirarDestacados(db);
            return json(res, 200, r);
        }

        // Auth check for all other API routes
        if (!autenticado(req)) {
            return json(res, 401, { error: 'no autenticado' });
        }

        // GET routes
        if (req.method === 'GET') {
            if (ruta === '/api/resumen') return json(res, 200, await getResumen());
            if (ruta === '/api/verificaciones') return json(res, 200, await getVerificaciones(url));
            if (ruta === '/api/publicaciones') return json(res, 200, await getPublicaciones(url));
            if (ruta === '/api/reportes') return json(res, 200, await getReportes(url));
            if (ruta === '/api/usuarios') return json(res, 200, await getUsuarios(url));
            if (ruta === '/api/stats') return json(res, 200, await getStats(url));
        }

        // POST routes
        if (req.method === 'POST') {
            const body = await leerCuerpo(req);
            const data = Object.fromEntries(body);

            // Verificaciones
            const vm = ruta.match(/^\/api\/verificaciones\/([^/]+)\/(aprobar|rechazar)$/);
            if (vm) {
                if (vm[2] === 'aprobar') await aprobarVerificacion(vm[1]);
                else await rechazarVerificacion(vm[1], data.motivo);
                return json(res, 200, { ok: true });
            }

            // Publicaciones
            const pm = ruta.match(/^\/api\/publicaciones\/([^/]+)\/(aprobar|finalizar|destacar|eliminar|estado)$/);
            if (pm) {
                const id = decodeURIComponent(pm[1]);
                const ref = db.collection('propiedades').doc(id);
                if (pm[2] === 'eliminar') {
                    await ref.delete();
                    return json(res, 200, { ok: true });
                }
                if (pm[2] === 'aprobar') {
                    await ref.set({ estado: 'publicado', aprobadoEn: new Date() }, { merge: true });
                    return json(res, 200, { ok: true });
                }
                if (pm[2] === 'estado') {
                    const nuevo = String(data.estado || '').trim();
                    const validos = ['publicado', 'disponible', 'finalizado', 'under_review', 'pendiente'];
                    if (!validos.includes(nuevo)) return json(res, 400, { error: 'estado invalido' });
                    await ref.set({ estado: nuevo, estadoCambiadoAdmin: true, estadoCambiadoEn: new Date() }, { merge: true });
                    return json(res, 200, { ok: true });
                }
                if (pm[2] === 'finalizar') { await ref.set({ estado: 'finalizado' }, { merge: true }); return json(res, 200, { ok: true }); }
                if (pm[2] === 'destacar') {
                    const doc = await ref.get();
                    const on = doc.get('isFeatured') === true;
                    if (on) {
                        await ref.set({ isFeatured: false, featuredUntil: null, destacadoDiasRestantes: 0, destacadoEstado: 'retirado' }, { merge: true });
                    } else {
                        const pedidos = Number(doc.get('destacadoDias'));
                        const dias = pedidos > 0 ? pedidos : 30;
                        const hasta = new Date(Date.now() + dias * 24 * 3600 * 1000);
                        await ref.set({ isFeatured: true, featuredUntil: hasta, destacadoDias: dias, destacadoDiasRestantes: dias, destacadoEstado: 'aprobado', solicitudDestacar: false, destacadoAprobadoEn: new Date(), destacadoAprobadoPor: 'admin-panel' }, { merge: true });
                    }
                    return json(res, 200, { ok: true });
                }
            }

            // Reportes
            const rm = ruta.match(/^\/api\/reportes\/([^/]+)\/resolver$/);
            if (rm) {
                await db.collection('reports').doc(decodeURIComponent(rm[1]))
                    .set({ estado: 'resuelto', resueltoEn: new Date() }, { merge: true });
                return json(res, 200, { ok: true });
            }
        }

        return json(res, 404, { error: 'not found' });
    } catch (e) {
        console.error('API ERROR', ruta, e);
        return json(res, 500, { error: e.message });
    }
}

// ---- JSON helper ----
function json(res, status, data) {
    res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify(data));
}

// ---- API Logic ----
async function getResumen() {
    const [prop, verif, rep, usr] = await Promise.all([
        db.collection('propiedades').count().get(),
        db.collection('verificaciones').count().get(),
        db.collection('reports').count().get(),
        db.collection('usuarios').count().get(),
    ]);
    const pendVerif = (await db.collection('verificaciones').where('estado', '==', 'pendiente').get()).size;
    const pendPub = (await db.collection('propiedades').where('estado', '==', 'under_review').get()).size;
    const pendRep = (await db.collection('reports').where('estado', '==', 'pendiente').get()).size;
    return {
        propiedades: { total: prop.data().count, pendientes: pendPub },
        verificaciones: { total: verif.data().count, pendientes: pendVerif },
        reportes: { total: rep.data().count, pendientes: pendRep },
        usuarios: { total: usr.data().count },
    };
}

async function getVerificaciones(url) {
    const q = (url.searchParams.get('q') || '').toLowerCase();
    const estado = url.searchParams.get('estado') || '';
    const page = parseInt(url.searchParams.get('page') || '1');
    const limit = parseInt(url.searchParams.get('limit') || '20');
    const snap = await db.collection('verificaciones').limit(500).get();
    let docs = snap.docs.map(ponerCreado)
        .filter((v) => !q || (v.email && v.email.toLowerCase().includes(q)) ||
            (v.nombre && v.nombre.toLowerCase().includes(q)) ||
            (v.numeroDocumento && v.numeroDocumento.toLowerCase().includes(q)))
        .filter((v) => !estado || v.estado === estado)
        .sort((a, b) => (mostrarCreado(b) > mostrarCreado(a) ? 1 : -1));
    const total = docs.length;
    docs = docs.slice((page - 1) * limit, page * limit);
    return { data: docs.map(v => ({
        ...v,
        pill: v.estado === 'aprobado' ? 'ok' : v.estado === 'rechazado' ? 'bad' : 'pend',
        creado: mostrarCreado(v),
    })), total, page, limit, totalPages: Math.ceil(total / limit) };
}

async function getPublicaciones(url) {
    const q = (url.searchParams.get('q') || '').toLowerCase();
    const estado = url.searchParams.get('estado') || '';
    const destacado = url.searchParams.get('destacado') || '';
    const page = parseInt(url.searchParams.get('page') || '1');
    const limit = parseInt(url.searchParams.get('limit') || '20');
    const snap = await db.collection('propiedades').limit(500).get();
    let docs = snap.docs.map(ponerCreado)
        .filter((p) => !q || (p.titulo && p.titulo.toLowerCase().includes(q)) ||
            (p.direccion && p.direccion.toLowerCase().includes(q)) ||
            (p.barrio && p.barrio.toLowerCase().includes(q)) ||
            (p.idPropietario && p.idPropietario.toLowerCase().includes(q)))
        .filter((p) => !estado || (p.estado || 'disponible') === estado)
        .filter((p) => !destacado || (destacado === 'si' ? p.isFeatured : !p.isFeatured))
        .sort((a, b) => (mostrarCreado(b) > mostrarCreado(a) ? 1 : -1));
    const total = docs.length;
    docs = docs.slice((page - 1) * limit, page * limit);
    return { data: docs.map(p => ({
        ...p,
        estado: p.estado || 'disponible',
        pill: p.estado === 'under_review' ? 'pend' : p.estado === 'finalizado' ? 'grey' : 'ok',
        creado: mostrarCreado(p),
        destacadoInfo: p.isFeatured ? {
            dias: p.destacadoDiasRestantes,
            vence: fecha(p.featuredUntil),
        } : null,
    })), total, page, limit, totalPages: Math.ceil(total / limit) };
}

async function getReportes(url) {
    const q = (url.searchParams.get('q') || '').toLowerCase();
    const estado = url.searchParams.get('estado') || '';
    const page = parseInt(url.searchParams.get('page') || '1');
    const limit = parseInt(url.searchParams.get('limit') || '20');
    const snap = await db.collection('reports').limit(500).get();
    let docs = snap.docs.map(ponerCreado)
        .filter((r) => !q || (r.motivo && r.motivo.toLowerCase().includes(q)) ||
            (r.listingId && r.listingId.toLowerCase().includes(q)) ||
            (r.reporterId && r.reporterId.toLowerCase().includes(q)))
        .filter((r) => !estado || (r.estado || 'pendiente') === estado)
        .sort((a, b) => (mostrarCreado(b) > mostrarCreado(a) ? 1 : -1));
    const total = docs.length;
    docs = docs.slice((page - 1) * limit, page * limit);
    return { data: docs.map(r => ({
        ...r,
        creado: mostrarCreado(r),
        pill: r.estado === 'resuelto' ? 'ok' : 'pend',
    })), total, page, limit, totalPages: Math.ceil(total / limit) };
}

async function getUsuarios(url) {
    const q = (url.searchParams.get('q') || '').toLowerCase();
    const tipo = url.searchParams.get('tipo') || '';
    const verif = url.searchParams.get('verif') || '';
    const trust = url.searchParams.get('trust') || '';
    const page = parseInt(url.searchParams.get('page') || '1');
    const limit = parseInt(url.searchParams.get('limit') || '20');
    const snap = await db.collection('usuarios').limit(500).get();
    let docs = snap.docs
        .filter((d) => {
            const u = d.data();
            return !q || (u.nombre && u.nombre.toLowerCase().includes(q)) ||
                (u.email && u.email.toLowerCase().includes(q)) ||
                d.id.toLowerCase().includes(q);
        })
        .filter((d) => !tipo || (d.data().tipoUsuario || d.data().role || '') === tipo)
        .filter((d) => {
            const u = d.data();
            const v = u.verification || {};
            const ok = u.verificationBadge === true || v.identityVerified === true;
            const status = v.status || '';
            if (verif === 'verificado') return ok;
            if (verif === 'pendiente') return status === 'pendiente';
            if (verif === 'sin_verificar') return !ok && status !== 'pendiente';
            return true;
        })
        .filter((d) => !trust || (d.data().trustLevel || 'nuevo') === trust)
        .map((d) => ({ id: d.id, ...d.data(), _creado: d.createTime ? d.createTime.toDate() : null }))
        .sort((a, b) => (mostrarCreado(b) > mostrarCreado(a) ? 1 : -1));
    const total = docs.length;
    docs = docs.slice((page - 1) * limit, page * limit);
    return { data: docs.map(u => {
        const v = u.verification || {};
        const ok = u.verificationBadge === true || v.identityVerified === true;
        return {
            ...u,
            verificado: ok,
            estadoVerif: v.status || '',
            creado: mostrarCreado(u),
        };
    }), total, page, limit, totalPages: Math.ceil(total / limit) };
}

async function getStats(url) {
    const days = parseInt(url.searchParams.get('days') || '30');
    const since = new Date(Date.now() - days * 24 * 3600 * 1000);
    const [props, users, chats] = await Promise.all([
        db.collection('propiedades').where('createdAt', '>=', since).count().get(),
        db.collection('usuarios').where('createdAt', '>=', since).count().get(),
        db.collection('chats').where('lastMessageAt', '>=', since).count().get(),
    ]);
    return {
        period: `${days}d`,
        newPropiedades: props.data().count,
        newUsuarios: users.data().count,
        newChats: chats.data().count,
    };
}

// ---- Actions ----
async function aprobarVerificacion(uid) {
    await db.collection('verificaciones').doc(uid).set({ estado: 'aprobado', motivo: '', revisadoEn: new Date() }, { merge: true });
    const ref = db.collection('usuarios').doc(uid);
    const doc = await ref.get();
    const actual = (doc.exists && doc.get('trustLevel')) || 'nuevo';
    await ref.set({
        verification: { status: 'aprobado', identityVerified: true, documentoPendiente: false, motivo: '' },
        verificationBadge: true,
        trustLevel: actual === 'nuevo' || actual === 'basic' ? 'verified' : actual,
    }, { merge: true });
}

async function rechazarVerificacion(uid, motivo) {
    await db.collection('verificaciones').doc(uid).set({ estado: 'rechazado', motivo, revisadoEn: new Date() }, { merge: true });
    await db.collection('usuarios').doc(uid).set({
        verification: { status: 'rechazado', identityVerified: false, documentoPendiente: false, motivo },
    }, { merge: true });
}

// ---- Server ----
const servidor = http.createServer(async (req, res) => {
    const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
    const ruta = url.pathname;

    try {
        // Auth endpoints (form-based for backward compat)
        if (ruta === '/login' && req.method === 'GET') {
            return serveStatic(req, res, path.join(PUBLIC_DIR, 'login.html'));
        }
        if (ruta === '/login' && req.method === 'POST') {
            const body = await leerCuerpo(req);
            const inputUser = body.get('user')?.trim() || '';
            const inputPassword = body.get('password')?.trim() || '';
            console.log('[LOGIN] Attempt:', {
                hasUser: !!USER, hasPassword: !!PASSWORD,
                inputUserLen: inputUser.length, expectedUserLen: USER.length,
                inputPassLen: inputPassword.length, expectedPassLen: PASSWORD.length
            });
            if (!PASSWORD) return html(res, 500, 'ADMIN_USER/ADMIN_PASSWORD no configurados en variables de entorno');
            if (inputUser !== USER || inputPassword !== PASSWORD) {
                console.log('[LOGIN] Failed: user or password mismatch');
                return html(res, 401, 'Usuario o contraseña incorrectos');
            }
            const id = crypto.randomBytes(16).toString('hex');
            const ts = Date.now().toString();
            const payload = `${id}|${ts}`;
            const firma = firmar(payload);
            // Cookie compatible con HTTPS (Cloud Run usa HTTPS)
            const isSecure = req.headers['x-forwarded-proto'] === 'https' || req.headers.host?.includes('.run.app');
            const cookieOpts = `alkil_admin=${payload}.${firma}; Path=/; HttpOnly; SameSite=Lax${isSecure ? '; Secure' : ''}; Max-Age=86400`;
            res.writeHead(302, {
                'Set-Cookie': cookieOpts,
                Location: '/',
            });
            return res.end();
        }
        if (ruta === '/logout') {
            const isSecure = req.headers['x-forwarded-proto'] === 'https' || req.headers.host?.includes('.run.app');
            res.writeHead(302, { 'Set-Cookie': `alkil_admin=; Path=/; HttpOnly; SameSite=Lax${isSecure ? '; Secure' : ''}; Max-Age=0`, Location: '/login' });
            return res.end();
        }

        // API routes
        if (ruta.startsWith('/api/')) {
            return handleAPI(req, res, url);
        }

        // SPA routes - serve index.html for client-side routing
        if (ruta === '/' || ruta.startsWith('/dashboard') || ruta.startsWith('/verificaciones') ||
            ruta.startsWith('/publicaciones') || ruta.startsWith('/reportes') || ruta.startsWith('/usuarios') ||
            ruta.startsWith('/stats')) {
            if (!autenticado(req)) {
                res.writeHead(302, { Location: '/login' });
                return res.end();
            }
            return serveStatic(req, res, path.join(PUBLIC_DIR, 'index.html'));
        }

        // Static assets
        const filePath = path.join(PUBLIC_DIR, ruta === '/' ? 'index.html' : ruta);
        if (fs.existsSync(filePath)) {
            return serveStatic(req, res, filePath);
        }

        // 404
        if (!autenticado(req) && ruta !== '/login') {
            res.writeHead(302, { Location: '/login' });
            return res.end();
        }
        res.writeHead(404, { 'Content-Type': 'text/html' });
        res.end('Not found');
    } catch (e) {
        console.error('ERROR', ruta, e);
        res.writeHead(500, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: e.message }));
    }
});

function html(res, status, msg) {
    res.writeHead(status, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end(`<!doctype html><html><body><h1>${status}</h1><p>${esc(msg)}</p><a href="/login">Volver</a></body></html>`);
}

servidor.listen(PORT, '0.0.0.0', () => {
    console.log(`AlkilApp Admin API + UI en http://localhost:${PORT}`);
    if (!PASSWORD) console.warn('AVISO: define ADMIN_PASSWORD');
});

process.on('unhandledRejection', (reason) => {
    console.error('UNHANDLED REJECTION:', reason);
});

process.on('uncaughtException', (err) => {
    console.error('UNCAUGHT EXCEPTION:', err);
});