// Comprueba el respaldo del departamento: un anuncio NUEVO de la app llega sin
// campo `departamento` (la app hace "ciudad" to departamento). Con el respaldo,
// la KPI y el filtro tienen que seguir viéndolo en su departamento.
const path = require('path');
const fs = require('fs');
const { Firestore } = require('@google-cloud/firestore');

const sa = path.join(__dirname, '..', 'credentials', 'alkilapp-seed-sa.json');
const db = new Firestore(Object.assign(
    { projectId: 'gen-lang-client-0040505884', databaseId: 'alkilappdb' },
    fs.existsSync(sa) ? { keyFilename: sa } : {}
));

let fallos = 0;
const ok = (c, m) => { console.log((c ? '  OK   ' : '  FALLA') + '  ' + m); if (!c) fallos++; };

(async () => {
    const snap = await db.collection('propiedades').limit(500).get();
    const pubs = snap.docs.map((d) => ({ id: d.id, ...d.data() }));

    // Replica el extractor del servidor.
    const departamentoDe = (p) => p.departamento || p.ciudad;

    const conCampo = pubs.filter((p) => p.departamento).length;
    const sinCampo = pubs.length - conCampo;
    console.log(`propiedades reales: ${pubs.length} (con campo departamento: ${conCampo}, sin el: ${sinCampo})\n`);

    console.log('=== KPI de departamento ===');
    const reparto = new Map();
    for (const p of pubs) {
        const d = departamentoDe(p);
        const k = d || 'sin_dato';
        reparto.set(k, (reparto.get(k) || 0) + 1);
    }
    for (const [k, n] of [...reparto].sort((a, b) => b[1] - a[1])) console.log(`  ${k.padEnd(14)} ${n}`);
    const suma = [...reparto.values()].reduce((a, b) => a + b, 0);
    ok(suma === pubs.length, `suma ${suma} == total ${pubs.length}`);

    // Un anuncio simulado tal como lo crea la app HOY: sin `departamento`.
    const nuevoComoLaApp = {
        id: 'simulado', titulo: 'Nuevo de la app', ciudad: 'Piura', tipo: 'casa',
        operacion: 'alquiler', precio: 500, moneda: 'PEN',
    };
    ok(!('departamento' in nuevoComoLaApp), 'el anuncio simulado viene SIN campo departamento');
    ok(departamentoDe(nuevoComoLaApp) === 'Piura', 'el respaldo lo lee como Piura (viene en ciudad)');
    ok(departamentoDe({ departamento: 'Lima', ciudad: 'Lima' }) === 'Lima', 'si el campo existe, gana el campo');
    ok(departamentoDe({ ciudad: 'Lima' }) === 'Lima', 'si no existe, cae a ciudad');
    ok((departamentoDe({}) || 'sin_dato') === 'sin_dato', 'si no hay ninguno, sin_dato (filtrable)');

    console.log('\n=== filtro con el respaldo ===');
    const filtrar = (v) => pubs.filter((p) => (departamentoDe(p) || 'sin_dato') === v);
    for (const v of [...reparto.keys()].filter((k) => k !== 'sin_dato')) {
        const n = filtrar(v).length;
        ok(n === reparto.get(v), `filtro departamento=${v} -> ${n} (KPI dice ${reparto.get(v)})`);
        ok(filtrar(v).every((p) => departamentoDe(p) === v), `  y todos son de ${v}`);
    }

    console.log('\n=== calidad: no debe dar falsos positivos ===');
    const m = require('./metricas.js');
    const conAmbo = pubs.filter((p) => (p.departamento || p.ciudad));
    const item = m.resumenCalidad(pubs).items.find((i) => i.campo === 'departamento');
    ok(item.faltan === pubs.length - conAmbo.length,
        `departamento: ${item.completos}/${item.faltan} completos, ${item.pct}% (esperado ${pubs.length - conAmbo.length} faltantes)`);

    console.log('\n' + (fallos ? `FALLOS: ${fallos}` : 'TODO OK'));
    process.exit(fallos ? 1 : 0);
})().catch((e) => { console.error(e); process.exit(1); });