#!/usr/bin/env node
/*
 * AlkilApp — Backfill de `departamento` en la coleccion `propiedades`.
 *
 * POR QUE
 * -------
 * La app Android nunca escribio un campo `departamento` en el documento de la
 * publicacion: `departamento` solo existe como concepto de UI (filtro/zona en
 * MainActivity) y lo que se guardaba era `ciudad`. Y lo que se guardaba en
 * `ciudad` son nombres de DEPARTAMENTO ("Huánuco", "Amazonas", "Lima"), no de
 * ciudad, asi que tampoco sirve como ciudad real.
 *
 * Consecuencia: cualquier KPI o filtro por departamento salia vacio, y el de
 * ciudad mostraba departamentos.
 *
 * QUE HACE
 * --------
 * Para cada publicacion sin `departamento`, deduce el departamento a partir de
 *   1. `distrito`/`barrio`, si alguno coincide con un departamento.
 *   2. `ciudad`, si coincide con un departamento (caso actual: "Lima",
 *      "Huánuco", "Amazonas").
 * y lo escribe. NO inventa: si nada coincide, deja el documento intacto y lo
 * reporta, porque un departamento equivocado es peor que uno vacio (contamina
 * las KPIs y el filtro con datos que nadie puede corregir desde el panel).
 *
 * ES IDEMPOTENTE: no vuelve a escribir si `departamento` ya esta puesto, y se
 * puede correr las veces que haga falta.
 *
 * USO
 *   node backend/admin/backfill-departamento.js            # simulacion
 *   node backend/admin/backfill-departamento.js --apply    # escribe
 */

const path = require('path');
const fs = require('fs');
const { Firestore } = require('@google-cloud/firestore');

const PROJECT_ID = 'gen-lang-client-0040505884';
const DATABASE_ID = 'alkilappdb';
const COLECCION = 'propiedades';

// Los 25 departamentos de Peru, en el MISMO orden y con la MISMA grafia que
// el array `departamentos_peru` de la app (strings.xml). Si la app agrega uno,
// hay que actualizar esta lista: el backfill compara por texto normalizado.
const DEPARTAMENTOS = [
    'Amazonas', 'Ancash', 'Apurímac', 'Arequipa', 'Ayacucho', 'Cajamarca',
    'Callao', 'Cusco', 'Huancavelica', 'Huánuco', 'Ica', 'Junín', 'La Libertad',
    'Lambayeque', 'Lima', 'Loreto', 'Madre de Dios', 'Moquegua', 'Pasco', 'Piura',
    'Puno', 'San Martín', 'Tacna', 'Tumbes', 'Ucayali',
];

/**
 * Normaliza para comparar: sin tildes, sin signos, en mayusculas y sin
 * espacios extra. Asi "Huanuco", "HUANUCO" y "Huánuco" son el mismo valor, y
 * se escribe siempre con la grafia oficial de la lista de arriba.
 */
function clave(v) {
    return String(v == null ? '' : v)
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '')
        .replace(/[^a-zA-Z0-9 ]/g, '')
        .trim()
        .replace(/\s+/g, ' ')
        .toUpperCase();
}

const POR_CLAVE = new Map(DEPARTAMENTOS.map((d) => [clave(d), d]));

function resolveCredentials() {
    const env = process.env.GOOGLE_APPLICATION_CREDENTIALS;
    if (env && fs.existsSync(env)) return env;
    const fallback = path.join(__dirname, '..', 'credentials', 'alkilapp-seed-sa.json');
    if (fs.existsSync(fallback)) return fallback;
    return null;
}

/** Deduce el departamento de una publicacion, o null si no hay coincidencia. */
function deduceDepartamento(p) {
    // Se prueban las fuentes de mayor a menor confianza. `distrito` y `barrio`
    // nunca son nombres de departamento, asi que solo sirven si alguien guardo
    // ahi un departamento; `ciudad` es el caso real de hoy.
    for (const campo of ['distrito', 'ciudad', 'barrio']) {
        const v = p[campo];
        if (!v) continue;
        const hit = POR_CLAVE.get(clave(v));
        if (hit) return { departamento: hit, origen: campo };
    }
    return null;
}

async function main() {
    const apply = process.argv.includes('--apply');
    const credencial = resolveCredentials();
    const db = new Firestore({
        projectId: PROJECT_ID,
        databaseId: DATABASE_ID,
        ...(credencial ? { keyFilename: credencial } : {}),
    });

    const snap = await db.collection(COLECCION).get();
    console.log(`Publicaciones revisadas: ${snap.size} | modo: ${apply ? 'ESCRITURA' : 'simulacion'}\n`);

    let escritos = 0, yaTenian = 0, sinInferencia = 0;
    const detalle = [];

    for (const doc of snap.docs) {
        const p = doc.data();
        if (p.departamento) { yaTenian++; continue; }

        const r = deduceDepartamento(p);
        if (!r) {
            sinInferencia++;
            detalle.push(`  SIN DATO    ${doc.id}  ciudad="${p.ciudad || ''}" barrio="${p.barrio || ''}" distrito="${p.distrito || ''}"`);
            continue;
        }
        if (apply) {
            await doc.ref.set({ departamento: r.departamento }, { merge: true });
        }
        escritos++;
        detalle.push(`  ${apply ? 'ESCRITO   ' : 'A ESCRIBIR'} ${doc.id}  ->  ${r.departamento}  (via ${r.origen}: "${p[r.origen]}")`);
    }

    detalle.forEach((l) => console.log(l));
    console.log(`\nResumen: ${escritos} a escribir/escritos | ${yaTenian} ya tenian departamento | ${sinInferencia} sin inferencia posible`);
    if (!apply && escritos) {
        console.log('\nPara aplicar de verdad, repetir con --apply');
    }
    if (sinInferencia) {
        console.log('\nLos "SIN DATO" NO se tocan a proposito: un departamento inventado');
        console.log('contaminaria las KPIs y el filtro con algo que nadie puede corregir');
        console.log('desde el panel. Se rellenan a mano o cuando la app escriba el campo.');
    }
}

main().catch((err) => { console.error(err); process.exit(1); });