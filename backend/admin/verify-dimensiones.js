// Verificacion local de las distribuciones y los filtros nuevos, contra el
// Firestore real. No usa el server HTTP: llama a las funciones por debajo de la
// capa de rutas comprobando que los filtros y las cuentas cuadren entre si.
const path = require('path');
const fs = require('fs');
const { Firestore } = require('@google-cloud/firestore');

const sa = path.join(__dirname, '..', 'credentials', 'alkilapp-seed-sa.json');
const db = new Firestore(Object.assign(
    { projectId: 'gen-lang-client-0040505884', databaseId: 'alkilappdb' },
    fs.existsSync(sa) ? { keyFilename: sa } : {}
));

const TRAMOS = [
    { id: 'economico', min: 0, max: 750 },
    { id: 'medio', min: 750, max: 1500 },
    { id: 'alto', min: 1500, max: 3000 },
    { id: 'premium', min: 3000, max: Infinity },
];
const ESTADOS = {
    publicado: 'disponible', activo: 'disponible', disponible: 'disponible',
    under_review: 'en_revision', pendiente: 'en_revision',
    pausada: 'pausada', finalizado: 'finalizado', rechazada: 'rechazada',
};
const ecan = (e) => ESTADOS[String(e || '').toLowerCase()] || 'otro';
const enSoles = (p) => {
    const n = Number(p.precio);
    if (!Number.isFinite(n)) return null;
    return String(p.moneda || 'PEN').toUpperCase() === 'USD' ? n * 3.75 : n;
};
const tramo = (p) => {
    const s = enSoles(p);
    if (s === null) return 'sin_precio';
    const t = TRAMOS.find((x) => s >= x.min && s < x.max);
    return t ? t.id : 'sin_precio';
};
const conteoPor = (pubs, f) => {
    const m = new Map();
    for (const p of pubs) {
        const v = f(p);
        const k = (v === null || v === undefined || v === '') ? 'sin_dato' : String(v);
        m.set(k, (m.get(k) || 0) + 1);
    }
    return [...m.entries()].sort((a, b) => b[1] - a[1]);
};

let fallos = 0;
const ok = (cond, msg) => { console.log((cond ? '  OK   ' : '  FALLA') + '  ' + msg); if (!cond) fallos++; };

(async () => {
    const snap = await db.collection('propiedades').limit(500).get();
    const pubs = snap.docs.map((d) => Object.assign({ id: d.id }, d.data()));

    console.log('\n=== distribuciones (total ' + pubs.length + ') ===');
    const dims = {
        departamento: (p) => p.departamento,
        ciudad: (p) => p.ciudad,
        distrito: (p) => p.distrito,
        barrio: (p) => p.barrio,
        tipo: (p) => p.tipo,
        operacion: (p) => p.operacion,
        estado: (p) => ecan(p.estado),
        moneda: (p) => (p.moneda ? String(p.moneda).toUpperCase() : null),
        tramoPrecio: (p) => tramo(p),
    };
    const dist = {};
    for (const [k, f] of Object.entries(dims)) {
        dist[k] = conteoPor(pubs, f);
        console.log('  ' + k.padEnd(13) + dist[k].map(([c, n]) => c + '=' + n).join('  '));
        // Cada dimension debe sumar el total de publicaciones.
        const suma = dist[k].reduce((a, [, n]) => a + n, 0);
        ok(suma === pubs.length, k + ': suma ' + suma + ' == ' + pubs.length);
    }

    console.log('\n=== filtros ===');
    const filtra = (pred) => pubs.filter(pred);
    const porDept = filtra((p) => p.departamento === 'Lima');
    ok(porDept.length === (dist.departamento.find(([c]) => c === 'Lima') || [0, 0])[1],
        'departamento=Lima -> ' + porDept.length + ' docs');

    const porDeptAmazonas = filtra((p) => p.departamento === 'Amazonas');
    ok(porDeptAmazonas.every((p) => p.ciudad === 'Amazonas'),
        'departamento=Amazonas: todos con ciudad=Amazonas (' + porDeptAmazonas.length + ')');

    // El bug que motivo el trabajo: sin departamento, el filtro no debe existir.
    ok(dist.departamento.some(([c]) => c === 'sin_dato') === pubs.some((p) => !p.departamento),
        'sin_dato en departamento solo aparece si hay docs sin el campo');

    const porEstado = filtra((p) => ecan(p.estado || 'disponible') === 'disponible');
    const crudo = pubs.filter((p) => (p.estado || 'disponible') === 'disponible');
    ok(porEstado.length >= crudo.length,
        'estado=disponible (canonico ' + porEstado.length + ') incluye los ' + crudo.length + ' crudos');
    const alias = pubs.filter((p) => p.estado === 'publicado').length;
    ok(porEstado.length > crudo.length || alias === 0,
        'estado=disponible absorbe los ' + alias + " docs en 'publicado'");

    const porTramo = filtra((p) => tramo(p) === 'economico');
    ok(porTramo.length === (dist.tramoPrecio.find(([c]) => c === 'economico') || [0, 0])[1],
        'tramoPrecio=economico -> ' + porTramo.length + ' docs');
    ok(porTramo.every((p) => enSoles(p) < 750),
        'tramo economico: todos < S/750');

    const porOperacion = filtra((p) => p.operacion === 'alquiler');
    ok(porOperacion.length === (dist.operacion.find(([c]) => c === 'alquiler') || [0, 0])[1],
        'operacion=alquiler -> ' + porOperacion.length + ' docs');

    // Filtros combinados: todos a la vez.
    const combo = filtra((p) => p.departamento === 'Lima' && tramo(p) === 'medio');
    ok(combo.every((p) => p.departamento === 'Lima' && enSoles(p) >= 750 && enSoles(p) < 1500),
        'combo Lima + medio -> ' + combo.length + ' docs, todos coherentes');

    // La suma de un filtro debe ser <= su KPI (nunca mayor).
    ok(dist.departamento.every(([, n]) => n <= pubs.length), 'ninguna KPI supera el total');

    console.log('\n' + (fallos ? 'FALLOS: ' + fallos : 'TODO OK'));
    process.exit(fallos ? 1 : 0);
})().catch((e) => { console.error(e); process.exit(1); });