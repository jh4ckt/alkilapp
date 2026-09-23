/* Censo real de Firestore (alkilappdb / propiedades) — para diagnosticar
 * "el feed solo muestra 1 de 4 en Lima". Lista TODOS los docs sin filtro.
 *
 * Uso:  node censo:propiedades.cjs
 * Credenciales: GOOGLE_APPLICATION_CREDENTIALS o fallback backend/credentials/alkilapp-seed-sa.json
 */
const path = require('path');
const fs = require('fs');
const { Firestore } = require('@google-cloud/firestore');

const PROJECT_ID = 'gen-lang-client-0040505884';
const DATABASE_ID = 'alkilappdb';

function resolveCredentials() {
  const env = process.env.GOOGLE_APPLICATION_CREDENTIALS;
  if (env && fs.existsSync(env)) return env;
  const fallback = path.join(__dirname, 'credentials', 'alkilapp-seed-sa.json');
  if (fs.existsSync(fallback)) return fallback;
  throw new Error('Sin credenciales. Pon GOOGLE_APPLICATION_CREDENTIALS o restaura backend/credentials/alkilapp-seed-sa.json');
}

const db = new Firestore({
  projectId: PROJECT_ID,
  databaseId: DATABASE_ID,
  keyFilename: resolveCredentials(),
});

async function main() {
  const snap = await db.collection('propiedades').get();
  const rows = [];
  for (const d of snap.docs) {
    const x = d.data();
    rows.push({
      id: d.id,
      titulo: String(x.titulo || x.titolo || x.title || '?').slice(0, 30),
      estado: x.estado ?? '(sin estado)',
      ciudad: x.ciudad ?? x.zonaCiudad ?? '?',
      zonaDepartamento: x.zonaDepartamento ?? x.departamento ?? x.zonaDepart ?? '?',
      zonaDistrito: x.zonaDistrito ?? x.distrito ?? x.zona ?? x.barrio ?? '?',
      precio: x.precio ?? x.price ?? 0,
      creado: x.createdAt && typeof x.createdAt.toDate === 'function'
        ? x.createdAt.toDate().toISOString()
        : '?',
      destacado: !!x.isFeatured,
    });
  }
  const estados = {};
  const lomas = [];
  for (const r of rows) {
    estados[r.estado] = (estados[r.estado] || 0) + 1;
    if (/lima/i.test(r.ciudad) || /lima/i.test(r.zonaDepartamento)) {
      lomas.push(r);
    }
  }
  console.log(`TOTAL en Firestore: ${rows.length}`);
  console.log(`POR ESTADO: ${JSON.stringify(estados)}`);
  console.log('---- LIMA (por zonaDepartamento/ciudad) ----');
  for (const r of lomas) {
    console.log(
      `  ${r.creado.slice(0, 19)}  [${r.zonaDepartamento} / ${r.zonaDistrito}] ${r.ciudad}  ` +
        `estado=${r.estado}  S/${r.precio}  ${r.titulo}  <${r.id.slice(-5)}>` +
        (r.destacado ? '  DESTACADA' : '')
    );
  }
  console.log(`\nEN LIMA: ${lomas.length} docs`);
  await db.terminate();
}

main().catch((e) => {
  console.error('ERR:', e.message);
  process.exit(1);
});
