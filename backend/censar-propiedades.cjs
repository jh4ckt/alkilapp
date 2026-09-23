const path = require('path');
const fs = require('fs');
const { Firestore } = require('@google-cloud/firestore');

function resolveCredentials() {
  const env = process.env.GOOGLE_APPLICATION_CREDENTIALS;
  if (env && fs.existsSync(env)) return env;
  const fallback = path.join(__dirname, 'credentials', 'alkilapp-seed-sa.json');
  if (fs.existsSync(fallback)) return fallback;
  throw new Error(
    'No credentials. Set GOOGLE_APPLICATION_CREDENTIALS or restore ' +
      'backend/credentials/alkilapp-seed-sa.json'
  );
}

const db = new Firestore({
  projectId: 'gen-lang-client-0040505884',
  databaseId: 'alkilappdb',
  keyFilename: resolveCredentials(),
});

async function main() {
  const snap = await db.collection('propiedades').get();
  const rows = [];
  for (const d of snap.docs) {
    const x = d.data();
    rows.push({
      id: d.id,
      titulo: String(x.titulo || x.title || '?').slice(0, 32),
      estado: x.estado ?? '(sin)',
      ciudad: x.ciudad ?? x.zonaCiudad ?? '?',
      zonaD: x.zonaDepartamento ?? x.departamento ?? '?',
      distrito: x.zonaDistrito ?? x.distrito ?? x.barrio ?? '?',
      precio: x.precio ?? x.price ?? 0,
      creado:
        x.createdAt && typeof x.createdAt.toDate === 'function'
          ? x.createdAt.toDate().toISOString()
          : '?',
    });
  }
  const estados = {};
  for (const r of rows) estados[r.estado] = (estados[r.estado] || 0) + 1;
  console.log(`TOTAL propiedades en Firestore: ${rows.length}`);
  console.log('POR ESTADO: ' + JSON.stringify(estados));
  console.log('---- detalle ----');
  for (const r of rows) {
    console.log(
      `  ${r.creado.slice(0, 19)}  [zonaD=${r.zonaD}] ${r.ciudad} ` +
        `[distrito=${r.distrito}] estado=${r.estado} S/${r.precio} ${r.titulo} <${r.id}>`
    );
  }
  await db.terminate();
}

main().catch((e) => {
  console.error('ERR:', e.message);
  process.exit(1);
});
