/*
 * AlkilApp Admin Panel - Modern API Server
 * Serves static frontend + REST API for admin operations
 */
const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
// FieldValue viene del mismo paquete que ya se usa para la db. Antes se sacaba de
// firebase-admin con initializeApp(applicationDefault()) y, si eso fallaba, se
// caia a un MOCK que devolvia {__arrayUnion:[...]}: al suspender un usuario eso
// escribia esa basura en historialSanciones en vez de un array. Este paquete no
// necesita ADC para los sentinelas, asi que ya no hay forma de que sea falso.
const { Firestore, FieldValue } = require('@google-cloud/firestore');
const { expirarDestacados } = require('./expirar');
const { cerrarChatsAcordados } = require('./cerrarChats');

const PROJECT = 'gen-lang-client-0040505884';
const DATABASE = 'alkilappdb';
const SA = process.env.GOOGLE_APPLICATION_CREDENTIALS ||
    path.join(__dirname, '..', 'credentials', 'alkilapp-seed-sa.json');
const PASSWORD = (process.env.ADMIN_PASSWORD || '').trim();
const USER = (process.env.ADMIN_USER || '').trim();
// Firmar sesiones de admin. SIN valor por defecto: un secreto conocido (por
// ejemplo "alkilapp-admin-dev" en un repo publico) deja que cualquiera falsifique
// la cookie alkil_admin y entre al panel con TODOS los permisos. Si falta, el
// proceso arranca pero el login queda imposible.
const SECRET = (process.env.ADMIN_SECRET || '').trim();
// El cron se autoriza con un secreto DISTINTO al de las sesiones: asi el token
// del cron no sirve como cookie de panel, ni al reves.
const CRON_SECRET = (process.env.CRON_SECRET || '').trim();
// Origen(es) autorizado(s) a llamar a /api/* desde el navegador. Vacio = ninguno
// (el panel es same-origin, asi que es lo correcto en produccion).
const ALLOWED_ORIGIN = (process.env.ALLOWED_ORIGIN || '').trim();
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

// Color del pill segun el estado real de la publicacion:
// en revision = amarillo, publicado/disponible = verde, finalizado = gris.
function pillEstado(estado) {
    switch (String(estado || '').toLowerCase()) {
        case 'publicado':
        case 'disponible': return 'ok';
        case 'under_review': return 'pend';
        case 'pendiente': return 'pend';
        case 'finalizado': return 'grey';
        case 'pausada': return 'grey';
        case 'rechazada': return 'bad';
        default: return 'new';
    }
}
const firmar = (t) => crypto.createHmac('sha256', SECRET).update(t).digest('hex');
const SESION_TTL = 24 * 60 * 60 * 1000;

/** Comparacion en tiempo constante: evita filtrar el secreto byte a byte. */
function igualSeguro(a, b) {
    const ba = Buffer.from(String(a == null ? '' : a));
    const bb = Buffer.from(String(b == null ? '' : b));
    if (ba.length !== bb.length) return false;
    return crypto.timingSafeEqual(ba, bb);
}

/**
 * Cabeceras de endurecimiento. Sin CSP la web del panel es susceptible a XSS
 * inyectado en el DOM (por eso Publicaciones.js escapa todo), y el frame-ancestors
 * evita que alguien meta el panel en un iframe clickjacking.
 */
function cabecerasSeguridad(res) {
    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader('X-Frame-Options', 'DENY');
    res.setHeader('Referrer-Pollow', 'no-referrer');
    res.setHeader('Content-Security-Policy',
        "default-src 'self'; img-src 'self' data: https:; style-src 'self' 'unsafe-inline'; "
        + "script-src 'self'; connect-src 'self'; form-action 'self'; frame-ancestors 'none'; base-uri 'self'");
}

/**
 * Limitador de intentos de login por IP: frena el fuerza bruta de la contraseña
 * del panel. En memoria (se reinicia al reiniciar la instancia, suficiente para
 * frenar ataques automatizados, no para un atacante persistente).
 */
const INTENTOS = new Map();
const MAX_INTENTOS = 8;
const VENTANA_INTENTOS = 10 * 60 * 1000;
function ipDe(req) {
    return (req.headers['x-forwarded-for'] || '').split(',')[0].trim()
        || req.socket?.remoteAddress || 'desconocida';
}
function bloqueado(ip) {
    const reg = INTENTOS.get(ip);
    if (!reg) return false;
    if (Date.now() - reg.desde > VENTANA_INTENTOS) { INTENTOS.delete(ip); return false; }
    return reg.n >= MAX_INTENTOS;
}
function anotarFallo(ip) {
    const reg = INTENTOS.get(ip);
    if (!reg || Date.now() - reg.desde > VENTANA_INTENTOS) {
        INTENTOS.set(ip, { n: 1, desde: Date.now() });
    } else {
        reg.n++;
    }
}
function limpiarIntentos(ip) { INTENTOS.delete(ip); }

function cookies(req) {
    const out = {};
    (req.headers.cookie || '').split(';').forEach((p) => {
        const i = p.indexOf('=');
        if (i > 0) out[p.slice(0, i).trim()] = decodeURIComponent(p.slice(i + 1).trim());
    });
    return out;
}

function autenticado(req) {
    // Fail-closed: sin ADMIN_SECRET no hay ninguna cookie valida. Sin esta guarda,
    // firmar() usaria una clave VACIA y cualquiera podria fabricar un token
    // (payload|ts + HMAC de "") y entrar al panel.
    if (!SECRET) return false;
    const token = cookies(req).alkil_admin;
    if (!token) return false;
    const [payload, firma] = token.split('.');
    if (!payload || !firma) return false;
    if (!igualSeguro(firma, firmar(payload))) return false;
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
        req.on('end', () => {
            const ct = req.headers['content-type'] || '';
            if (ct.includes('application/json')) {
                try {
                    const obj = JSON.parse(data);
                    // Return the parsed object directly
                    resolve(obj && typeof obj === 'object' ? obj : {});
                } catch {
                    resolve({});
                }
            } else {
                resolve(Object.fromEntries(new URLSearchParams(data)));
            }
        });
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

    // CORS: el panel se sirve desde el MISMO origen, asi que no necesita CORS.
    // Con "Access-Control-Allow-Origin: *" cualquier pagina podia leer las
    // respuestas de /api/* con la cookie del admin. Ahora solo se permite un
    // origen explicito (ALLOWED_ORIGIN) y por defecto ninguno.
    const origenPermitido = ALLOWED_ORIGIN;
    const origen = req.headers.origin;
    if (origenPermitido && origen === origenPermitido) {
        res.setHeader('Access-Control-Allow-Origin', origenPermitido);
        res.setHeader('Vary', 'Origin');
        res.setHeader('Access-Control-Allow-Methods', 'GET,POST,PUT,PATCH,DELETE,OPTIONS');
        res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
    }

    if (req.method === 'OPTIONS') {
        res.writeHead(204); return res.end();
    }

    try {
        // Cron (no auth): se autoriza con CRON_SECRET, no con el secreto de sesión.
        if (ruta === '/api/cron/expirar') {
            if (!CRON_SECRET || !igualSeguro(url.searchParams.get('clave'), CRON_SECRET)) {
                return json(res, 403, { error: 'clave invalida' });
            }
            const r = await expirarDestacados(db);
            return json(res, 200, r);
        }

        // Cierre automatico de los chats con acuerdo cerrado (48h).
        if (ruta === '/api/cron/cerrar-chats') {
            if (!CRON_SECRET || !igualSeguro(url.searchParams.get('clave'), CRON_SECRET)) {
                return json(res, 403, { error: 'clave invalida' });
            }
            const r = await cerrarChatsAcordados(db);
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
            // Detalle de una sola publicacion, para la vista previa del modal.
            // El listado NO trae las fotos (ver getPublicaciones) porque son
            // base64 de ~100KB cada una: mandarlas ahi hacia que la respuesta
            // de 5 filas pasara de 1MB.
            const pubDet = ruta.match(/^\/api\/publicaciones\/([^/]+)$/);
            if (pubDet) {
                const det = await getPublicacionDetalle(decodeURIComponent(pubDet[1]));
                return json(res, det.status || 200, det);
            }
            if (ruta === '/api/reportes') return json(res, 200, await getReportes(url));
            if (ruta === '/api/usuarios') return json(res, 200, await getUsuarios(url));
            if (ruta === '/api/stats') return json(res, 200, await getStats(url));
            if (ruta === '/api/soporte') return json(res, 200, await getSoporte(url));
        }

        // PATCH routes
        if (req.method === 'PATCH') {
            const data = await leerCuerpo(req);

            // Tickets de soporte: cambiar estado
            const sm = ruta.match(/^\/api\/soporte\/([^/]+)\/estado$/);
            if (sm) {
                const id = decodeURIComponent(sm[1]);
                const nuevo = String(data.estado || '').trim().toLowerCase();
                const validos = ['pendiente', 'en_proceso', 'atendido', 'resuelto', 'cerrado'];
                if (!validos.includes(nuevo)) {
                    return json(res, 400, { error: `estado invalido. Valores: ${validos.join(', ')}` });
                }
                const ref = db.collection('soporte').doc(id);
                const doc = await ref.get();
                if (!doc.exists) return json(res, 404, { error: 'Ticket no encontrado' });
                await ref.set({
                    estado: nuevo,
                    fechaActualizacion: new Date(),
                    actualizadoPor: 'admin-panel',
                }, { merge: true });
                return json(res, 200, { ok: true, estado: nuevo });
            }
        }

        // POST routes
        if (req.method === 'POST') {
            const data = await leerCuerpo(req);

            // Verificaciones
            const vm = ruta.match(/^\/api\/verificaciones\/([^/]+)\/(aprobar|rechazar)$/);
            if (vm) {
                if (vm[2] === 'aprobar') await aprobarVerificacion(vm[1]);
                else await rechazarVerificacion(vm[1], data.motivo);
                return json(res, 200, { ok: true });
            }

            // Publicaciones
            const pm = ruta.match(/^\/api\/publicaciones\/([^/]+)\/(aprobar|finalizar|destacar|destacado|eliminar|estado)$/);
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
                    // Estados reales de la app + alias historicos de este panel
                    const mapaEstados = {
                        'activa': 'disponible',
                        'activo': 'disponible',
                        'pendiente': 'pendiente',
                        'en_revision': 'under_review',
                        'pausada': 'pausada',
                        'rechazada': 'rechazada',
                        'publicado': 'publicado',
                        'disponible': 'disponible',
                        'finalizado': 'finalizado',
                        'under_review': 'under_review'
                    };
                    const estadoInterno = mapaEstados[nuevo.toLowerCase()];
                    if (!estadoInterno) return json(res, 400, { error: 'estado invalido' });
                    await ref.set({ estado: estadoInterno, estadoCambiadoAdmin: true, estadoCambiadoEn: new Date() }, { merge: true });
                    return json(res, 200, { ok: true, estado: estadoInterno });
                }
                if (pm[2] === 'finalizar') { await ref.set({ estado: 'finalizado' }, { merge: true }); return json(res, 200, { ok: true }); }
                // `destacar` alterna; `destacado` (el que llama el panel) fija el valor
                if (pm[2] === 'destacar' || pm[2] === 'destacado') {
                    const doc = await ref.get();
                    if (!doc.exists) return json(res, 404, { error: 'Publicacion no encontrada' });
                    const forzado = pm[2] === 'destacado'
                        ? (data.destacado === true || data.destacado === 'true')
                        : doc.get('isFeatured') !== true;
                    const on = forzado;
                    if (!on) {
                        await ref.set({ isFeatured: false, featuredUntil: null, destacadoDiasRestantes: 0, destacadoEstado: 'retirado' }, { merge: true });
                    } else {
                        const pedidos = Number(doc.get('destacadoDias'));
                        const dias = pedidos > 0 ? pedidos : 30;
                        const hasta = new Date(Date.now() + dias * 24 * 3600 * 1000);
                        await ref.set({ isFeatured: true, featuredUntil: hasta, destacadoDias: dias, destacadoDiasRestantes: dias, destacadoEstado: 'aprobado', solicitudDestacar: false, destacadoAprobadoEn: new Date(), destacadoAprobadoPor: 'admin-panel' }, { merge: true });
                    }
                    return json(res, 200, { ok: true, destacado: on });
                }
            }

            // Usuarios - Cambiar estado
            const um = ruta.match(/^\/api\/usuarios\/([^/]+)\/estado$/);
            if (um && req.method === 'POST') {
                const id = decodeURIComponent(um[1]);
                const nuevo = String(data.estado || '').trim();
                const validos = ['activo', 'suspendido', 'desactivado'];
                if (!validos.includes(nuevo)) return json(res, 400, { error: 'estado invalido' });
                const motivo = String(data.motivo || '').trim();
                const reporteId = String(data.reporteId || '').trim();
                await db.collection('usuarios').doc(id).set({ 
                    estado: nuevo, 
                    estadoCambiadoAdmin: true, 
                    estadoCambiadoEn: new Date(),
                    historialSanciones: FieldValue.arrayUnion({
                        estado: nuevo,
                        motivo,
                        reporteId,
                        fecha: new Date()
                    })
                }, { merge: true });
                return json(res, 200, { ok: true });
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
    const [prop, verif, rep, usr, sop] = await Promise.all([
        db.collection('propiedades').count().get(),
        db.collection('verificaciones').count().get(),
        db.collection('reports').count().get(),
        db.collection('usuarios').count().get(),
        db.collection('soporte').count().get(),
    ]);
    const pendVerif = (await db.collection('verificaciones').where('estado', '==', 'pendiente').get()).size;
    const pendPub = (await db.collection('propiedades').where('estado', '==', 'under_review').get()).size;
    const pendRep = (await db.collection('reports').where('estado', '==', 'pendiente').get()).size;
    const pendSop = (await db.collection('soporte').where('estado', '==', 'pendiente').get()).size;
    return {
        propiedades: { total: prop.data().count, pendientes: pendPub },
        verificaciones: { total: verif.data().count, pendientes: pendVerif },
        reportes: { total: rep.data().count, pendientes: pendRep },
        usuarios: { total: usr.data().count },
        soporte: { total: sop.data().count, pendientes: pendSop },
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
    
    // Fetch all owner names in batch
    const propietarioIds = new Set();
    const propiedadesRaw = snap.docs.map(ponerCreado);
    propiedadesRaw.forEach(p => {
        if (p.idPropietario) propietarioIds.add(p.idPropietario);
    });
    
    // Fetch owner names in batch
    const propietariosMap = new Map();
    if (propietarioIds.size > 0) {
        const chunks = Array.from(propietarioIds);
        for (let i = 0; i < chunks.length; i += 10) {
            const chunk = chunks.slice(i, i + 10);
            const snaps = await Promise.all(chunk.map(id => db.collection('usuarios').doc(id).get()));
            snaps.forEach(doc => {
                if (doc.exists) {
                    const d = doc.data();
                    propietariosMap.set(doc.id, d.nombre || d.name || d.email?.split('@')[0] || 'Sin nombre');
                }
            });
        }
    }
    
    let docs = propiedadesRaw
        .filter((p) => !q || (p.titulo && p.titulo.toLowerCase().includes(q)) ||
            (p.direccion && p.direccion.toLowerCase().includes(q)) ||
            (p.barrio && p.barrio.toLowerCase().includes(q)) ||
            (p.idPropietario && p.idPropietario.toLowerCase().includes(q)))
        .filter((p) => !estado || (p.estado || 'disponible') === estado)
        .filter((p) => {
            if (!destacado) return true;
            const pedirSi = destacado === 'si' || destacado === 'true';
            const pedirNo = destacado === 'no' || destacado === 'false';
            if (pedirNo) return p.isFeatured !== true;
            return p.isFeatured === true;
        })
        .sort((a, b) => (mostrarCreado(b) > mostrarCreado(a) ? 1 : -1));
    
    const total = docs.length;
    docs = docs.slice((page - 1) * limit, page * limit);
    
    return { data: docs.map(p => {
        const ownerName = p.idPropietario ? propietariosMap.get(p.idPropietario) || 'Propietario sin nombre' : 'Sin propietario';
        const destacadoTipo = p.isFeatured && p.destacadoDias ? `destacado_${p.destacadoDias}d` : null;
        // Las fotos NO viajan en el listado: son base64 de ~100KB cada una y
        // con 5 filas la respuesta pasaba de 1MB. Solo se manda cuantos hay,
        // para que la fila pueda avisar "3 fotos" y la vista previa las pida
        // al endpoint de detalle.
        const { fotos, imagenUrl, ...resto } = p;

        return {
            ...resto,
            propietarioNombre: ownerName,
            operacion: p.operacion || 'alquiler',
            tipoInmueble: p.tipo || 'departamento',
            estado: p.estado || 'disponible',
            pill: pillEstado(p.estado),
            creado: mostrarCreado(p),
            totalFotos: (Array.isArray(fotos) ? fotos.length : 0) || (Array.isArray(imagenUrl) ? imagenUrl.length : 0),
            destacadoInfo: p.isFeatured ? {
                dias: p.destacadoDiasRestantes,
                vence: fecha(p.featuredUntil),
                tipo: destacadoTipo,
            } : null,
        };
    }), total, page, limit, totalPages: Math.ceil(total / limit) };
}

// Detalle completo de una publicacion, incluidas las fotos, para la vista
// previa del admin. Trae tambien los datos del propietario (verificacion,
// telefono, email) porque al revisar un inmueble hay que poder juzgar quien
// lo publico.
async function getPublicacionDetalle(id) {
    if (!id || id.length > 150) return { error: 'id invalido', status: 400 };
    const snap = await db.collection('propiedades').doc(id).get();
    if (!snap.exists) return { error: 'La publicación no existe', status: 404 };

    const p = { id: snap.id, ...snap.data() };

    // La app Android guarda las fotos como base64 CRUDO, sin el prefijo
    // "data:image/jpeg;base64," (empieza directo con /9j/ en los .jpg). Un
    // <img src="/9j/4AAQ..."> no renderiza nada, asi que hay que reconstruir la
    // data URL: /9j/ es la firma de JPEG, iVBOR de PNG. Solo se aceptan esas
    // dos familias ademas de https, porque el CSP del panel es
    // img-src 'self' data: https: y cualquier otro esquema quedaria bloqueado.
    const aDataUrl = (f) => {
        if (typeof f !== 'string') return null;
        const s = f.trim();
        if (!s) return null;
        if (s.startsWith('data:image')) return s;
        if (s.startsWith('https://')) return s;
        if (s.startsWith('/9j/')) return 'data:image/jpeg;base64,' + s;
        if (s.startsWith('iVBORw0KGgo')) return 'data:image/png;base64,' + s;
        return null;
    };

    const fotos = (Array.isArray(p.fotos) ? p.fotos : []).map(aDataUrl).filter(Boolean);
    const imagenes = (Array.isArray(p.imagenUrl) ? p.imagenUrl : []).map(aDataUrl).filter(Boolean);

    let propietario = null;
    if (p.idPropietario) {
        const du = await db.collection('usuarios').doc(p.idPropietario).get();
        if (du.exists) {
            const u = du.data();
            propietario = {
                id: du.id,
                nombre: u.nombre || u.name || 'Sin nombre',
                email: u.email || null,
                telefono: u.telefono || null,
                verificado: u.verificado === true || u.emailVerified === true,
                trustLevel: u.trustLevel || null,
                verificaciones: u.verificaciones || null,
                fechaRegistro: fecha(u.fechaRegistro || u.createdAt),
            };
        }
    }

    return {
        data: {
            id: p.id,
            titulo: p.titulo || 'Sin título',
            descripcion: p.descripcion || '',
            direccion: p.direccion || '',
            barrio: p.barrio || '',
            ciudad: p.ciudad || '',
            operacion: p.operacion || 'alquiler',
            tipo: p.tipo || 'departamento',
            precio: p.precio ?? null,
            moneda: p.moneda || 'PEN',
            ambientes: p.ambientes ?? null,
            superficieM2: p.superficieM2 ?? null,
            comodidades: Array.isArray(p.comodidades) ? p.comodidades : [],
            lat: p.lat ?? null,
            lng: p.lng ?? null,
            estado: p.estado || 'disponible',
            pill: pillEstado(p.estado),
            isFeatured: p.isFeatured === true,
            destacadoDias: p.destacadoDias ?? null,
            destacadoDiasRestantes: p.destacadoDiasRestantes ?? null,
            publicadoEn: fecha(p.publicadoEn),
            creado: mostrarCreado(p),
            estadoCambiadoAdmin: p.estadoCambiadoAdmin === true,
            totalFotos: fotos.length + imagenes.length,
            // data: URL (base64) o https. El front decide como pintar cada una.
            // Tope de 8: cada foto pesa ~100KB en base64 y un announcing con 20
            // son 2MB en una sola respuesta. `totalFotos` avisa si se truncó.
            fotos: [...fotos, ...imagenes].slice(0, 8),
            propietario,
        },
    };
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

// Tickets de soporte: listado ordenado por fechaCreacion descendente
async function getSoporte(url) {
    const q = (url.searchParams.get('q') || '').toLowerCase();
    const estado = url.searchParams.get('estado') || '';
    const page = parseInt(url.searchParams.get('page') || '1');
    const limit = parseInt(url.searchParams.get('limit') || '20');
    const snap = await db.collection('soporte').limit(500).get();
    let docs = snap.docs.map(ponerCreado)
        .filter((t) => !q || (t.asunto && t.asunto.toLowerCase().includes(q)) ||
            (t.mensaje && t.mensaje.toLowerCase().includes(q)) ||
            (t.emailContacto && t.emailContacto.toLowerCase().includes(q)) ||
            (t.usuarioId && t.usuarioId.toLowerCase().includes(q)))
        .filter((t) => !estado || (t.estado || 'pendiente') === estado)
        // fechaCreacion descendente (los docs sin fecha van al final)
        .sort((a, b) => {
            const ta = a.fechaCreacion ? new Date(a.fechaCreacion).getTime() : 0;
            const tb = b.fechaCreacion ? new Date(b.fechaCreacion).getTime() : 0;
            return tb - ta;
        });
    const total = docs.length;
    docs = docs.slice((page - 1) * limit, page * limit);
    return {
        data: docs.map(t => ({
            ...t,
            estado: t.estado || 'pendiente',
            pill: t.estado === 'resuelto' || t.estado === 'cerrado' ? 'ok'
                : t.estado === 'en_proceso' || t.estado === 'atendido' ? 'pend' : 'pend',
            creado: mostrarCreado(t),
        })),
        total, page, limit, totalPages: Math.ceil(total / limit),
    };
}

// Periodos del selector de la vista Estadisticas (?period=) y su equivalencia
// en dias. El dashboard sigue llamando con ?days=N.
const PERIODOS_STATS = { '7d': 7, '30d': 30, '90d': 90, '1y': 365 };
const MESES_CORTOS = ['Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun', 'Jul', 'Ago', 'Sep', 'Oct', 'Nov', 'Dic'];

// Divide el rango pedido en tramos (dia / semana / mes) para poder dibujar la
// evolucion sin inventar numeros. El tramo mensual va por mes calendario: con
// ventanas fijas de 30 dias se repiten nombres ("Ene", "Ene", "May", "May").
function tramosStats(dias) {
    const ahora = Date.now();
    const tramos = [];

    if (dias > 90) {
        const n = Math.max(2, Math.round(dias / 30.44));
        for (let i = n - 1; i >= 0; i--) {
            const fin = new Date(ahora);
            fin.setDate(1);
            fin.setMonth(fin.getMonth() - i + 1); // primer dia del mes siguiente
            const ini = new Date(fin);
            ini.setMonth(ini.getMonth() - 1);
            tramos.push({
                ini: ini.getTime(), fin: fin.getTime(),
                etiqueta: MESES_CORTOS[ini.getMonth()]
            });
        }
        return tramos;
    }

    const pasoDias = dias <= 7 ? 1 : 7;
    const n = Math.ceil(dias / pasoDias);
    for (let i = n - 1; i >= 0; i--) {
        const fin = ahora - i * pasoDias * 24 * 3600 * 1000;
        const ini = fin - pasoDias * 24 * 3600 * 1000;
        const f = new Date(fin);
        tramos.push({ ini, fin, etiqueta: `${f.getDate()}/${f.getMonth() + 1}` });
    }
    return tramos;
}

// Cuenta quantos documentos caen en cada tramo usando su campo de fecha; si el
// documento no lo tiene (imports antiguos, seed) cae a la fecha de creacion de
// Firestore, que es la misma que usa el listado del panel.
function seriePorTramos(docs, campos, tramos) {
    const valores = new Array(tramos.length).fill(0);
    for (const doc of docs) {
        let v = null;
        for (const c of campos) {
            const x = doc.get(c);
            if (x) { v = x; break; }
        }
        if (!v) v = doc.createTime;
        const t = typeof v?.toDate === 'function' ? v.toDate().getTime() : (v ? new Date(v).getTime() : NaN);
        if (!Number.isFinite(t)) continue;
        for (let i = tramos.length - 1; i >= 0; i--) {
            if (t >= tramos[i].ini && t < tramos[i].fin) { valores[i]++; break; }
        }
    }
    return valores;
}

// Campo de fecha de cada coleccion. No es el mismo en todas: los usuarios se
// registran con 'fechaRegistro' y las publicaciones con 'publicadoEn'; solo
// verificaciones usan 'createdAt'. Se listan en orden de preferencia.
const CAMPOS_FECHA = {
    usuarios: ['fechaRegistro', 'createdAt'],
    propiedades: ['publicadoEn', 'createdAt'],
};

// Documentos "creados" dentro del rango. Firestore no tiene OR entre campos, asi
// que se consulta cada candidato y se fusiona por id (un doc nunca se cuenta
// dos veces). createTime queda como ultimo recurso en seriePorTramos.
async function documentosEnRango(coleccion, since) {
    const campos = CAMPOS_FECHA[coleccion] || ['createdAt'];
    const resultados = await Promise.all(
        campos.map(c => db.collection(coleccion).where(c, '>=', since).get())
    );
    const vistos = new Map();
    resultados.forEach(s => s.docs.forEach(d => vistos.set(d.id, d)));
    return [...vistos.values()];
}

// Suma varios count() sobre los mismos alias de estado (el histórico mezcla
// 'publicado'/'activo'/'disponible' para lo mismo, ver Property.estadoNormalizado).
async function contarPorAlias(coleccion, alias) {
    const counts = await Promise.all(
        alias.map(e => db.collection(coleccion).where('estado', '==', e).count().get())
    );
    return counts.reduce((suma, c) => suma + c.data().count, 0);
}

async function getStats(url) {
    // El selector de la vista Estadisticas envia ?period=7d|30d|90d|1y y el
    // dashboard ?days=N. Antes solo se leia "days", asi que cambiar el periodo
    // era un no-op y la vista siempre devolvia los mismos 30 dias.
    const period = (url.searchParams.get('period') || '').toLowerCase();
    let dias = PERIODOS_STATS[period];
    if (!dias) {
        dias = parseInt(url.searchParams.get('days') || '30', 10);
        if (!Number.isFinite(dias) || dias <= 0 || dias > 365) dias = 30;
    }
    const periodLabel = PERIODOS_STATS[period] ? period : `${dias}d`;
    const since = new Date(Date.now() - dias * 24 * 3600 * 1000);

    // propsEnRango / usuariosEnRango sustituyen a los count(): el KPI y la
    // grafica deben salir del MISMO conjunto de documentos, o el "+N del
    // periodo" no cuadra con la curva.
    const [propsEnRango, usuariosEnRango, chats, dispPub, pausada, revision, finalizada, apVerif, penVerif, rejVerif] = await Promise.all([
        documentosEnRango('propiedades', since),
        documentosEnRango('usuarios', since),
        db.collection('chats').where('lastMessageAt', '>=', since).count().get(),
        contarPorAlias('propiedades', ['disponible', 'publicado', 'activo']),
        contarPorAlias('propiedades', ['pausada']),
        contarPorAlias('propiedades', ['under_review', 'pendiente']),
        contarPorAlias('propiedades', ['finalizado']),
        contarPorAlias('verificaciones', ['aprobado']),
        contarPorAlias('verificaciones', ['pendiente']),
        contarPorAlias('verificaciones', ['rechazado']),
    ]);

    const tramos = tramosStats(dias);
    return {
        period: periodLabel,
        dias,
        newPropiedades: propsEnRango.length,
        newUsuarios: usuariosEnRango.length,
        newChats: chats.data().count,
        // Series reales. Antes el endpoint no devolvia ninguna y el front
        // pintaba datos inventados (Sem 1: 12, Sem 2: 19...).
        usuariosTrend: { labels: tramos.map(t => t.etiqueta), values: seriePorTramos(usuariosEnRango, CAMPOS_FECHA.usuarios, tramos) },
        propiedadesTrend: { labels: tramos.map(t => t.etiqueta), values: seriePorTramos(propsEnRango, CAMPOS_FECHA.propiedades, tramos) },
        propiedadesDist: {
            disponible: dispPub,
            pausada,
            revision,
            finalizado: finalizada,
        },
        verificacionesDist: { aprobadas: apVerif, pendientes: penVerif, rechazadas: rejVerif },
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

    // Cabeceras de seguridad en TODAS las respuestas (estaticas, panel y API).
    cabecerasSeguridad(res);

    try {
        // Auth endpoints (form-based for backward compat)
        if (ruta === '/login' && req.method === 'GET') {
            return serveStatic(req, res, path.join(PUBLIC_DIR, 'login.html'));
        }
        if (ruta === '/login' && req.method === 'POST') {
            const ip = ipDe(req);
            if (bloqueado(ip)) {
                console.log(`[LOGIN] Bloqueado por exceso de intentos: ${ip}`);
                return html(res, 429, 'Demasiados intentos fallidos. Espera 10 minutos.');
            }
            const body = await leerCuerpo(req);
            const inputUser = (typeof body.get === 'function' ? body.get('user') : body.user)?.trim() || '';
            const inputPassword = (typeof body.get === 'function' ? body.get('password') : body.password)?.trim() || '';
            console.log('[LOGIN] Attempt:', {
                hasUser: !!USER, hasPassword: !!PASSWORD, hasSecret: !!SECRET,
                inputUserLen: inputUser.length, expectedUserLen: USER.length,
                inputPassLen: inputPassword.length, expectedPassLen: PASSWORD.length
            });
            if (!PASSWORD) return html(res, 500, 'ADMIN_USER/ADMIN_PASSWORD no configurados en variables de entorno');
            if (!SECRET) return html(res, 500, 'ADMIN_SECRET no configurado: no se pueden firmar sesiones');
            if (!igualSeguro(inputUser, USER) || !igualSeguro(inputPassword, PASSWORD)) {
                anotarFallo(ip);
                console.log('[LOGIN] Failed: user or password mismatch');
                return html(res, 401, 'Usuario o contraseña incorrectos');
            }
            limpiarIntentos(ip);
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
            ruta.startsWith('/soporte') || ruta.startsWith('/stats')) {
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
    if (!PASSWORD) console.warn('AVISO: define ADMIN_PASSWORD (el login no funcionara)');
    if (!SECRET) console.warn('AVISO: define ADMIN_SECRET (las sesiones no se podran firmar)');
    if (!CRON_SECRET) console.warn('AVISO: define CRON_SECRET (los crones responderan 403)');
});

process.on('unhandledRejection', (reason) => {
    console.error('UNHANDLED REJECTION:', reason);
});

process.on('uncaughtException', (err) => {
    console.error('UNCAUGHT EXCEPTION:', err);
});