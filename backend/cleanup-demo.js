const { Firestore } = require('@google-cloud/firestore');
const path = require('path');
const fs = require('fs');

const PROJECT_ID = 'gen-lang-client-0040505884';
const DATABASE_ID = 'alkilappdb';

function resolveCredentials() {
  const env = process.env.GOOGLE_APPLICATION_CREDENTIALS;
  if (env && fs.existsSync(env)) return env;
  const fallback = path.join(__dirname, 'credentials', 'alkilapp-seed-sa.json');
  if (fs.existsSync(fallback)) return fallback;
  throw new Error('No credentials found');
}

const db = new Firestore({
  projectId: PROJECT_ID,
  databaseId: DATABASE_ID,
  keyFilename: resolveCredentials(),
});

async function cleanAllDemoData() {
  console.log('[cleanup] Conectando a', PROJECT_ID, '/', DATABASE_ID);
  let deleted = 0;

  // 1. Propiedades: borrar donde esDemo == true O id empieza con "seed-" O "demo-"
  console.log('[cleanup] Limpiando propiedades (esDemo / seed- / demo-)...');
  const propsSnap = await db.collection('propiedades').get();
  for (const doc of propsSnap.docs) {
    const data = doc.data();
    const id = doc.id;
    if (data.esDemo === true || id.startsWith('seed-') || id.startsWith('demo-')) {
      await doc.ref.delete();
      console.log(`  - borrado: ${id} (${data.titulo || 'sin título'})`);
      deleted++;
    } else {
      console.log(`  - MANTENIDO (real): ${id} - ${data.titulo || 'sin título'}`);
    }
  }

  // 2. Chats: borrar los de demo + chats ligados a listings seed
  console.log('[cleanup] Limpiando chats...');
  const chatsSnap = await db.collection('chats').get();
  for (const doc of chatsSnap.docs) {
    const data = doc.data();
    const listingId = data.listingId;
    if (listingId && (listingId.startsWith('seed-') || listingId.startsWith('demo-'))) {
      // borrar mensajes primero
      const msgsSnap = await doc.ref.collection('messages').get();
      await Promise.all(msgsSnap.docs.map(m => m.ref.delete()));
      await doc.ref.delete();
      console.log(`  - chat borrado: ${doc.id} (listing: ${listingId})`);
      deleted++;
    }
  }

  // 3. Usuarios seed (los 4 del seed)
  console.log('[cleanup] Limpiando usuarios seed...');
  const seedUserIds = ['u-propietario-1', 'u-inquilino-1', 'u-ambos-1', 'u-propietario-2'];
  for (const uid of seedUserIds) {
    const uRef = db.collection('usuarios').doc(uid);
    // borrar subcolección reviews
    const revSnap = await uRef.collection('reviews').get();
    await Promise.all(revSnap.docs.map(d => d.ref.delete()));
    await uRef.delete();
    console.log(`  - usuario seed borrado: ${uid}`);
    deleted++;
  }

  // 4. Verificaciones de usuarios seed
  console.log('[cleanup] Limpiando verificaciones seed...');
  const verSnap = await db.collection('verificaciones').get();
  for (const doc of verSnap.docs) {
    const data = doc.data();
    if (seedUserIds.includes(data.uid) || seedUserIds.includes(doc.id)) {
      await doc.ref.delete();
      console.log(`  - verificación borrada: ${doc.id}`);
      deleted++;
    }
  }

  // 5. Reportes vinculados a listings seed
  console.log('[cleanup] Limpiando reportes...');
  const repSnap = await db.collection('reports').get();
  for (const doc of repSnap.docs) {
    const data = doc.data();
    if (data.listingId && (data.listingId.startsWith('seed-') || data.listingId.startsWith('demo-'))) {
      await doc.ref.delete();
      console.log(`  - reporte borrado: ${doc.id}`);
      deleted++;
    }
  }

  console.log(`[cleanup] Completado. ${deleted} documentos borrados. Solo quedan publicaciones reales.`);
}

cleanAllDemoData()
  .then(() => process.exit(0))
  .catch(e => { console.error('[cleanup] ERROR:', e); process.exit(1); });