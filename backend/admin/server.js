/*
 * Panel de administracion de AlkilApp (Node puro, sin dependencias extra).
 *
 *   ADMIN_PASSWORD=...            obligatorio (password de acceso)
 *   ADMIN_SECRET=...              opcional, firma la cookie de sesion
 *   PORT=8080                     puerto (Cloud Run lo inyecta)
 *
 * Ejecutar en local:  node admin/server.js
 * Desplegar:          ver admin/README.md
 */
const http = require('http');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const { Firestore } = require('@google-cloud/firestore');
const { expirarDestacados } = require('../expirar');

const PROJECT = 'gen-lang-client-0040505884';
const DATABASE = 'alkilappdb';
const SA = process.env.GOOGLE_APPLICATION_CREDENTIALS ||
    path.join(__dirname, '..', 'credentials', 'alkilapp-seed-sa.json');
const PASSWORD = process.env.ADMIN_PASSWORD || '';
const SECRET = process.env.ADMIN_SECRET || 'alkilapp-admin-dev';
const PORT = Number(process.env.PORT || 8099);

// En local usamos el JSON de la service account; en Cloud Run basta con las
// credenciales por defecto del servicio (ADC), por eso solo se pasa si existe.
const opcionesDb = { projectId: PROJECT, databaseId: DATABASE };
if (fs.existsSync(SA)) opcionesDb.keyFilename = SA;
const db = new Firestore(opcionesDb);
const sesiones = new Set();

// ---------------------------------------------------------------------------
// Utilidades
// ---------------------------------------------------------------------------
const esc = (s) => String(s == null ? '' : s).replace(/[&<>"']/g, (c) => (
    { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]
));

const fecha = (v) => {
    if (!v) return '-';
    const d = typeof v.toDate === 'function' ? v.toDate() : new Date(v);
    return isNaN(d) ? '-' : d.toISOString().slice(0, 16).replace('T', ' ');
};

// Fecha de creación: usa el campo propio si existe (seed/app), si no el
// createTime del documento (Firestore lo fija en el primer write siempre).
const mostrarCreado = (doc) => fecha(doc.createdAt || doc._creado);
const ponerCreado = (d) => ({ id: d.id, ...d.data(), _creado: d.createTime ? d.createTime.toDate() : null });

const firmar = (t) => crypto.createHmac('sha256', SECRET).update(t).digest('hex');

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
    const [id, firma] = token.split('.');
    return id && firma === firmar(id) && sesiones.has(id);
}

function leerCuerpo(req) {
    return new Promise((resolve) => {
        let data = '';
        req.on('data', (c) => {
            data += c;
            if (data.length > 5_000_000) req.destroy();
        });
        req.on('end', () => resolve(new URLSearchParams(data)));
    });
}

function html(res, cuerpo, titulo = 'AlkilApp Admin', status = 200) {
    res.writeHead(status, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end(`<!doctype html><html lang="es"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>${esc(titulo)}</title>
<style>
:root{--primary:#1E293B;--accent:#FF6B5E;--gold:#D4A017;--bg:#F8FAFC;
--card:#fff;--border:#E2E8F0;--muted:#64748B;--ok:#3AA37B;--bad:#DC2626}
*{box-sizing:border-box}
body{margin:0;font:14px/1.5 -apple-system,Segoe UI,Roboto,sans-serif;background:var(--bg);color:var(--primary)}
header{background:var(--primary);color:#fff;padding:12px 20px;display:flex;
align-items:center;gap:18px;flex-wrap:wrap}
header b{font-size:17px}
header a{color:#cbd5e1;text-decoration:none;padding:4px 8px;border-radius:8px}
header a:hover{background:#334155;color:#fff}
header .sp{flex:1}
main{max-width:1080px;margin:24px auto;padding:0 16px}
.card{background:var(--card);border:1px solid var(--border);border-radius:14px;
padding:18px;margin-bottom:16px}
.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(170px,1fr));gap:12px}
.stat{background:var(--card);border:1px solid var(--border);border-radius:14px;padding:16px}
.stat b{display:block;font-size:26px}
.stat span{color:var(--muted);font-size:12px;text-transform:uppercase;letter-spacing:.04em}
table{width:100%;border-collapse:collapse}
th,td{text-align:left;padding:9px 8px;border-bottom:1px solid var(--border);vertical-align:top}
th{color:var(--muted);font-size:12px;text-transform:uppercase;letter-spacing:.04em}
img.doc{max-width:340px;border-radius:10px;border:1px solid var(--border);display:block;margin-top:8px}
.pill{display:inline-block;padding:2px 9px;border-radius:999px;font-size:11px;font-weight:600}
.pill.pend{background:#FEF3C7;color:#92400E}
.pill.ok{background:#DCFCE7;color:#166534}
.pill.bad{background:#FEE2E2;color:#991B1B}
.pill.gold{background:#FBF1D6;color:#8A6100}
.pill.grey{background:#E2E8F0;color:#334155}
button{border:0;border-radius:9px;padding:8px 13px;font-size:13px;font-weight:600;
cursor:pointer;margin:2px 4px 2px 0}
.b-ok{background:var(--ok);color:#fff}
.b-bad{background:var(--bad);color:#fff}
.b-gold{background:var(--gold);color:#fff}
.b-grey{background:#E2E8F0;color:var(--primary)}
input[type=text],input[type=password]{width:100%;padding:10px;border:1px solid var(--border);
border-radius:10px;font-size:15px;margin:6px 0 14px}
.muted{color:var(--muted);font-size:12px}
a.coral{color:var(--accent)}
</style></head><body>${cuerpo}</body></html>`);
}

function nav(activo) {
    const link = (href, txt) =>
        `<a href="${href}"${activo === href ? ' style="background:#334155;color:#fff"' : ''}>${txt}</a>`;
    return `<header><b>AlkilApp Admin</b>
${link('/', 'Resumen')}${link('/verificaciones', 'Verificaciones')}
${link('/publicaciones', 'Publicaciones')}${link('/reportes', 'Denuncias')}
${link('/usuarios', 'Usuarios')}<span class="sp"></span>
<form method="post" action="/logout" style="margin:0">
<button class="b-grey" type="submit">Salir</button></form></header>`;
}

function buscarInput(nombre, valor, placeholder) {
    return `<form method="get" style="margin-bottom:12px;display:flex;gap:8px;flex-wrap:wrap">
<input type="text" name="${nombre}" value="${esc(valor)}" placeholder="${esc(placeholder)}"
style="flex:1;min-width:220px;padding:8px 12px;border:1px solid var(--border);border-radius:8px;font-size:14px">
<button class="b-ok" type="submit" style="padding:8px 14px">Buscar</button>
${valor ? `<a href="${nombre === 'q' ? '/' : '/' + nombre}" class="b-grey" style="padding:8px 14px;text-decoration:none">Limpiar</a>` : ''}
</form>`;
}

function aviso(msg, tipo = 'ok') {
    if (!msg) return '';
    return `<div class="card" style="border-color:${tipo === 'ok' ? '#bbf7d0' : '#fecaca'}">
<b>${tipo === 'ok' ? 'Listo' : 'Error'}:</b> ${esc(msg)}</div>`;
}

// ---------------------------------------------------------------------------
// Vistas
// ---------------------------------------------------------------------------
function vistaLogin(error) {
    return `<main style="max-width:380px;margin-top:12vh">
<div class="card">
<h2 style="margin:0 0 4px">Panel de administracion</h2>
<p class="muted">AlkilApp</p>
${error ? `<p style="color:var(--bad)">${esc(error)}</p>` : ''}
<form method="post" action="/login">
<input type="password" name="password" placeholder="Password de admin" autofocus>
<button class="b-ok" style="width:100%;padding:11px" type="submit">Ingresar</button>
</form></div></main>`;
}

async function vistaResumen() {
    const [prop, verif, rep, usr] = await Promise.all([
        db.collection('propiedades').count().get(),
        db.collection('verificaciones').count().get(),
        db.collection('reports').count().get(),
        db.collection('usuarios').count().get(),
    ]);
    const pendVerif = (await db.collection('verificaciones')
        .where('estado', '==', 'pendiente').get()).size;
    const pendPub = (await db.collection('propiedades')
        .where('estado', '==', 'under_review').get()).size;
    const pendRep = (await db.collection('reports')
        .where('estado', '==', 'pendiente').get()).size;
    const stat = (n, t, extra = '') =>
        `<div class="stat"><b>${n}</b><span>${t}</span>${extra}</div>`;
    return nav('/') + `<main><h2>Resumen</h2><div class="grid">
${stat(prop.data().count, 'Inmuebles')}
${stat(pendPub, 'Por aprobar', '<span class="muted">publicaciones en revision</span>')}
${stat(verif.data().count, 'Verificaciones', `<span class="muted">${pendVerif} pendientes</span>`)}
${stat(rep.data().count, 'Denuncias', `<span class="muted">${pendRep} pendientes</span>`)}
${stat(usr.data().count, 'Usuarios')}
</div></main>`;
}

async function vistaVerificaciones(avisoHtml, q = '') {
    let query = db.collection('verificaciones').limit(200);
    if (q) {
        // Buscar en email, nombre, numeroDocumento
        // Nota: Firestore no soporta OR nativo, filtramos en memoria tras traer limit
    }
    const snap = await query.get();
    const docs = snap.docs.map(ponerCreado)
        .filter((v) => !q || (v.email && v.email.toLowerCase().includes(q.toLowerCase())) ||
            (v.nombre && v.nombre.toLowerCase().includes(q.toLowerCase())) ||
            (v.numeroDocumento && v.numeroDocumento.toLowerCase().includes(q.toLowerCase())))
        .sort((a, b) => (mostrarCreado(b) > mostrarCreado(a) ? 1 : -1));
    const filas = docs.map((v) => {
        const pill = v.estado === 'aprobado' ? 'ok' : v.estado === 'rechazado' ? 'bad' : 'pend';
        const acciones = v.estado === 'pendiente' ? `
<form method="post" action="/verificaciones/${esc(v.id)}/aprobar" style="display:inline">
<button class="b-ok" type="submit">Aprobar</button></form>
<form method="post" action="/verificaciones/${esc(v.id)}/rechazar" style="display:inline">
<input type="hidden" name="motivo" value="Documento ilegible">
<button class="b-bad" type="submit">Rechazar</button></form>` : '';
        return `<div class="card">
<div style="display:flex;gap:10px;align-items:center;flex-wrap:wrap">
<b>${esc(v.nombre || '(sin nombre)')}</b>
<span class="pill ${pill}">${esc(v.estado)}</span>
<span class="muted">${esc(v.email)}</span>
</div>
<div class="muted">${esc(v.tipoDocumento)} ${esc(v.numeroDocumento)} - enviado ${mostrarCreado(v)}</div>
${v.imagen ? `<img class="doc" alt="documento" src="data:image/jpeg;base64,${esc(v.imagen)}">` : '<p class="muted">sin imagen</p>'}
${v.motivo ? `<p class="muted">motivo: ${esc(v.motivo)}</p>` : ''}
<div style="margin-top:10px">${acciones}</div></div>`;
    }).join('');
    return nav('/verificaciones') + `<main><h2>Verificaciones de identidad</h2>
${buscarInput('q', q, 'Buscar email, nombre, documento...')}
${avisoHtml || ''}${filas || '<div class="card">Sin solicitudes.</div>'}</main>`;
}

async function vistaPublicaciones(avisoHtml) {
    const snap = await db.collection('propiedades').limit(200).get();
    const docs = snap.docs.map(ponerCreado);
    const filas = docs.map((p) => {
        const estado = p.estado || 'disponible';
        const pill = estado === 'under_review' ? 'pend' : estado === 'finalizado' ? 'grey' : 'ok';
        const pub = p.publicado === false ? ' <span class="pill grey">oculto</span>' : '';
        let gold = '';
        if (p.isFeatured) {
            const restan = p.destacadoDiasRestantes;
            const vence = p.featuredUntil ? fecha(p.featuredUntil) : '-';
            gold = ` <span class="pill gold">destacado${restan != null ? ` - ${esc(restan)} dia(s)` : ''}</span>`
                + `<div class="muted">vence ${esc(vence)}</div>`;
        }
        if (p.solicitudDestacar === true) {
            gold += ` <span class="pill pend">pidio destacar${p.destacadoDias ? ` (${esc(p.destacadoDias)} dias)` : ''}</span>`;
        }
        const aprobar = estado === 'under_review'
            ? `<form method="post" action="/publicaciones/${esc(p.id)}/aprobar" style="display:inline"><button class="b-ok" type="submit">Aprobar</button></form>`
            : '';
        const destacarTxt = p.isFeatured ? 'Quitar destacado'
            : (p.destacadoDias ? `Destacar ${esc(p.destacadoDias)} dias` : 'Destacar 30 dias');
        const destacar = `<form method="post" action="/publicaciones/${esc(p.id)}/destacar" style="display:inline"><button class="b-gold" type="submit">${destacarTxt}</button></form>`;
        const cerrar = estado !== 'finalizado'
            ? `<form method="post" action="/publicaciones/${esc(p.id)}/finalizar" style="display:inline"><button class="b-grey" type="submit">Finalizar</button></form>`
            : '';
        const opcionesEstado = ['publicado', 'disponible', 'finalizado', 'under_review', 'pendiente']
            .map((e) => `<option value="${e}"${e === estado ? ' selected' : ''}>${e}</option>`)
            .join('');
        const cambiarEstado = `<form method="post" action="/publicaciones/${esc(p.id)}/estado" style="display:inline"><select name="estado" style="padding:3px">${opcionesEstado}</select><button class="b-grey" type="submit">Cambiar</button></form>`;
        const tituloSeguro = String(p.titulo || p.id).replace(/'/g, '');
        const borrar = `<form method="post" action="/publicaciones/${esc(p.id)}/eliminar" style="display:inline" onsubmit="return confirm('Eliminar definitivamente &quot;${esc(tituloSeguro)}&quot;? Esta accion no se puede deshacer.');"><button class="b-bad" type="submit">Eliminar</button></form>`;
        return `<tr>
<td><b>${esc(p.titulo || '(sin titulo)')}</b>${pub}${gold}
<div class="muted">${esc(p.direccion)} - ${esc(p.barrio)}, ${esc(p.ciudad)}</div></td>
<td><span class="pill ${pill}">${esc(estado)}</span></td>
<td>${p.precio != null ? esc(p.precio) : '-'}</td>
<td class="muted">${mostrarCreado(p)}</td>
<td>${esc(p.idPropietario).slice(0, 10)}...</td>
<td>${aprobar}${cambiarEstado}${destacar}${cerrar}${borrar}</td></tr>`;
    }).join('');
    return nav('/publicaciones') + `<main><h2>Publicaciones</h2>${avisoHtml || ''}
<div class="card" style="overflow-x:auto"><table>
<tr><th>Inmueble</th><th>Estado</th><th>Precio</th><th>Publicado</th><th>Dueno</th><th>Acciones</th></tr>
${filas || '<tr><td colspan="6">Sin inmuebles.</td></tr>'}</table></div></main>`;
}

async function vistaReportes(avisoHtml) {
    const snap = await db.collection('reports').limit(200).get();
    const docs = snap.docs.map(ponerCreado)
        .sort((a, b) => (mostrarCreado(b) > mostrarCreado(a) ? 1 : -1));
    const filas = docs.map((r) => `<tr>
<td><b>${esc(r.motivo || '-')}</b>
<div class="muted">${esc(r.detalle || '')}</div></td>
<td>${esc(r.listingId || '-')}</td>
<td class="muted">${mostrarCreado(r)}</td>
<td>${esc(r.reporterId || '-')}</td>
<td>${estadoPillReporte(r.estado)}</td>
<td>${r.estado === 'pendiente'
            ? `<form method="post" action="/reportes/${esc(r.id)}/resolver" style="display:inline"><button class="b-ok" type="submit">Resolver</button></form>`
            : ''}</td></tr>`).join('');
    return nav('/reportes') + `<main><h2>Denuncias</h2>${avisoHtml || ''}
<div class="card" style="overflow-x:auto"><table>
<tr><th>Motivo</th><th>Inmueble</th><th>Creado</th><th>Denunciante</th><th>Estado</th><th></th></tr>
${filas || '<tr><td colspan="6">Sin denuncias.</td></tr>'}</table></div></main>`;
}

function estadoPillReporte(e) {
    const cls = e === 'resuelto' ? 'ok' : 'pend';
    return `<span class="pill ${cls}">${esc(e || 'pendiente')}</span>`;
}

async function vistaUsuarios(avisoHtml) {
    const snap = await db.collection('usuarios').limit(300).get();
    const filas = snap.docs.map((d) => {
        const u = d.data();
        const verif = u.verification || {};
        const ok = u.verificationBadge === true || verif.identityVerified === true;
        return `<tr>
<td><b>${esc(u.nombre || '(sin nombre)')}</b>
<div class="muted">${esc(u.email || d.id)}</div></td>
<td>${esc(u.tipoUsuario || u.role || '-')}</td>
<td>${ok ? '<span class="pill ok">verificado</span>' : '<span class="pill grey">sin verificar</span>'}
${verif.status === 'pendiente' ? ' <span class="pill pend">en revision</span>' : ''}</td>
<td>${esc(u.trustLevel || 'nuevo')}</td>
<td class="muted">${mostrarCreado(u)}</td>
<td>${u.rating != null ? esc(u.rating) : '-'}</td></tr>`;
    }).join('');
    return nav('/usuarios') + `<main><h2>Usuarios</h2>${avisoHtml || ''}
<div class="card" style="overflow-x:auto"><table>
<tr><th>Usuario</th><th>Tipo</th><th>Identidad</th><th>Confianza</th><th>Alta</th><th>Rating</th></tr>
${filas || '<tr><td colspan="6">Sin usuarios.</td></tr>'}</table></div></main>`;
}

// ---------------------------------------------------------------------------
// Acciones
// ---------------------------------------------------------------------------
async function aprobarVerificacion(uid) {
    await db.collection('verificaciones').doc(uid).set(
        { estado: 'aprobado', motivo: '', revisadoEn: new Date() }, { merge: true });
    const ref = db.collection('usuarios').doc(uid);
    const doc = await ref.get();
    const actual = (doc.exists && doc.get('trustLevel')) || 'nuevo';
    await ref.set({
        verification: {
            status: 'aprobado',
            identityVerified: true,
            documentoPendiente: false,
            motivo: '',
        },
        verificationBadge: true,
        trustLevel: actual === 'nuevo' || actual === 'basic' ? 'verified' : actual,
    }, { merge: true });
}

async function rechazarVerificacion(uid, motivo) {
    await db.collection('verificaciones').doc(uid).set(
        { estado: 'rechazado', motivo, revisadoEn: new Date() }, { merge: true });
    await db.collection('usuarios').doc(uid).set({
        verification: {
            status: 'rechazado',
            identityVerified: false,
            documentoPendiente: false,
            motivo,
        },
    }, { merge: true });
}

// ---------------------------------------------------------------------------
// Servidor
// ---------------------------------------------------------------------------
const servidor = http.createServer(async (req, res) => {
    const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
    const ruta = url.pathname;

    try {
        if (ruta === '/login' && req.method === 'GET') {
            return html(res, vistaLogin(''), 'Ingresar');
        }
        if (ruta === '/login' && req.method === 'POST') {
            const cuerpo = await leerCuerpo(req);
            if (!PASSWORD) {
                return html(res, vistaLogin('ADMIN_PASSWORD no esta configurado'), 'Ingresar', 500);
            }
            if (cuerpo.get('password') !== PASSWORD) {
                return html(res, vistaLogin('Password incorrecto'), 'Ingresar', 401);
            }
            const id = crypto.randomBytes(16).toString('hex');
            sesiones.add(id);
            res.writeHead(302, {
                'Set-Cookie': `alkil_admin=${id}.${firmar(id)}; Path=/; HttpOnly; SameSite=Lax`,
                Location: '/',
            });
            return res.end();
        }
        if (ruta === '/logout') {
            const token = cookies(req).alkil_admin;
            if (token) sesiones.delete(token.split('.')[0]);
            res.writeHead(302, {
                'Set-Cookie': 'alkil_admin=; Path=/; Max-Age=0',
                Location: '/login',
            });
            return res.end();
        }

        // Caducidad de destacados: la llama Cloud Scheduler (sin sesion).
        // Protegida con ADMIN_SECRET en la query.
        if (ruta === '/cron/expirar') {
            if (!SECRET || url.searchParams.get('clave') !== SECRET) {
                res.writeHead(403, { 'Content-Type': 'application/json' });
                return res.end(JSON.stringify({ error: 'clave invalida' }));
            }
            const resultado = await expirarDestacados(db);
            res.writeHead(200, { 'Content-Type': 'application/json' });
            return res.end(JSON.stringify(resultado));
        }

        if (!autenticado(req)) {
            res.writeHead(302, { Location: '/login' });
            return res.end();
        }
        if (req.method === 'GET') {
            if (ruta === '/') return html(res, await vistaResumen());
            if (ruta === '/verificaciones') return html(res, await vistaVerificaciones());
            if (ruta === '/publicaciones') return html(res, await vistaPublicaciones());
            if (ruta === '/reportes') return html(res, await vistaReportes());
            if (ruta === '/usuarios') return html(res, await vistaUsuarios());
            return html(res, nav('') + '<main><div class="card">No encontrado</div></main>', '404', 404);
        }

        if (req.method === 'POST') {
            const m = ruta.match(/^\/verificaciones\/([^/]+)\/(aprobar|rechazar)$/);
            if (m) {
                const uid = decodeURIComponent(m[1]);
                if (m[2] === 'aprobar') {
                    await aprobarVerificacion(uid);
                } else {
                    const cuerpo = await leerCuerpo(req);
                    await rechazarVerificacion(uid, cuerpo.get('motivo') || 'Documento no valido');
                }
                return html(res, await vistaVerificaciones('Verificacion actualizada.'));
            }
            const p = ruta.match(/^\/publicaciones\/([^/]+)\/(aprobar|finalizar|destacar|eliminar|estado)$/);
            if (p) {
                const id = decodeURIComponent(p[1]);
                const ref = db.collection('propiedades').doc(id);
                if (p[2] === 'eliminar') {
                    await ref.delete();
                    return html(res, await vistaPublicaciones('Publicacion eliminada.'));
                }
                if (p[2] === 'aprobar') {
                    // "publicado": la app lo lee como disponible en el feed
                    // (estadoNormalizado) y habilita el chat/seguimiento.
                    await ref.set({ estado: 'publicado', aprobadoEn: new Date() }, { merge: true });
                    return html(res, await vistaPublicaciones('Aprobada y publicada (' + esc(ref.id) + ').'));
                }
                if (p[2] === 'estado') {
                    const cuerpo = await leerCuerpo(req);
                    const nuevo = String(cuerpo.get('estado') || '').trim();
                    const validos = ['publicado', 'disponible', 'finalizado', 'under_review', 'pendiente'];
                    if (!validos.includes(nuevo)) {
                        return html(res, await vistaPublicaciones('Estado invalido: ' + esc(nuevo)));
                    }
                    await ref.set({
                        estado: nuevo,
                        estadoCambiadoAdmin: true,
                        estadoCambiadoEn: new Date(),
                    }, { merge: true });
                    return html(res, await vistaPublicaciones('Estado cambiado a "' + esc(nuevo) + '".'));
                }
                if (p[2] === 'finalizar') await ref.set({ estado: 'finalizado' }, { merge: true });
                if (p[2] === 'destacar') {
                    const doc = await ref.get();
                    const on = doc.get('isFeatured') === true;
                    if (on) {
                        await ref.set({
                            isFeatured: false,
                            featuredUntil: null,
                            destacadoDiasRestantes: 0,
                            destacadoEstado: 'retirado',
                        }, { merge: true });
                    } else {
                        const pedidos = Number(doc.get('destacadoDias'));
                        const dias = pedidos > 0 ? pedidos : 30;
                        const hasta = new Date(Date.now() + dias * 24 * 3600 * 1000);
                        await ref.set({
                            isFeatured: true,
                            featuredUntil: hasta,
                            destacadoDias: dias,
                            destacadoDiasRestantes: dias,
                            destacadoEstado: 'aprobado',
                            solicitudDestacar: false,
                            destacadoAprobadoEn: new Date(),
                            destacadoAprobadoPor: 'admin-panel',
                        }, { merge: true });
                    }
                }
                return html(res, await vistaPublicaciones('Publicacion actualizada.'));
            }
            const r = ruta.match(/^\/reportes\/([^/]+)\/resolver$/);
            if (r) {
                await db.collection('reports').doc(decodeURIComponent(r[1]))
                    .set({ estado: 'resuelto', resueltoEn: new Date() }, { merge: true });
                return html(res, await vistaReportes('Denuncia resuelta.'));
            }
        }

        return html(res, nav('') + '<main><div class="card">No encontrado</div></main>', '404', 404);
    } catch (e) {
        console.error('ERROR', ruta, e);
        return html(res, `<main><div class="card"><b>Error:</b> ${esc(e.message)}</div></main>`, 'Error', 500);
    }
});

servidor.listen(PORT, () => {
    console.log(`Panel AlkilApp en http://localhost:${PORT}`);
    if (!PASSWORD) console.warn('AVISO: define ADMIN_PASSWORD para poder entrar.');
});
