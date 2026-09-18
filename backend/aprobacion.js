#!/usr/bin/env node
/*
 * AlkilApp — Script para aprobar/listar propiedades pendientes revisión
 * Uso:
 *   node backend/aprobacion.js          -> lista las propiedades en under_review
 *   node backend/aprobacion.js --id <id>   -> aprueba esa propiedad (cambia estado a disponible)
 *   node backend/aprobacion.js --todos     -> aprueba TODAS las propiedades en under_review
 *
 * Requiere credenciales de Google Cloud (ver seed.js).
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
  throw new Error(
    'No find credentials. Set GOOGLE_APPLICATION_CREDENTIALS or restore ' +
      'backend/credentials/alkilapp-seed-sa.json'
  );
}

const db = new Firestore({
  projectId: PROJECT_ID,
  databaseId: DATABASE_ID,
  keyFilename: resolveCredentials(),
});

async function listarPendientes() {
  const snap = await db.collection('propiedades')
    .where('estado', '==', 'under_review')
    .get();
  if (snap.empty) {
    console.log('No hay propiedades en under_review.');
    return;
  }
  console.log(`Propiedades pendientes (${snap.size}):`);
  snap.forEach(doc => {
    const data = doc.data();
    console.log(`- ${doc.id}: título="${data.titulo}", dueño=${data.idPropietario}, estado=${data.estado}`);
  });
}

async function aprobarPropiedad(id) {
  const docRef = db.collection('propiedades').doc(id);
  const snap = await docRef.get();
  if (!snap.exists) {
    console.log(`Propiedad ${id} no encontrada.`);
    return;
  }
  const data = snap.data();
  if (data.estado !== 'under_review') {
    console.log(`La propiedad ${id} no está en under_review (actual: ${data.estado}).`);
    return;
  }
  await docRef.update({
    estado: 'disponible',
    aprobadoEn: new Date(),
    aprobadoPor: 'admin-cli',
  });
  console.log(`Propiedad ${id} aprobada y cambiada a disponible.`);
}

async function aprobarTodos() {
  const snap = await db.collection('propiedades')
    .where('estado', '==', 'under_review')
    .get();
  if (snap.empty) {
    console.log('No hay propiedades en under_review para aprobar.');
    return;
  }
  console.log(`Aprobando ${snap.size} propiedades pendientes...`);
  const batch = db.batch();
  snap.docs.forEach(doc => {
    const ref = db.collection('propiedades').doc(doc.id);
    batch.update(ref, {
      estado: 'disponible',
      aprobadoEn: new Date(),
      aprobadoPor: 'admin-cli',
    });
  });
  await batch.commit();
  console.log('Todas aprobadas.');
}

// CLI
const args = process.argv.slice(2);
const cmd = args[0];

if (cmd === '--id' || cmd === '-i') {
  const id = args[1];
  if (!id) { console.error('Falta el ID de la propiedad.'); process.exit(1); }
  aprobarPropiedad(id).catch(err => { console.error(err); process.exit(1); });
} else if (cmd === '--todos' || cmd === '-a') {
  aprobarTodos().catch(err => { console.error(err); process.exit(1); });
} else {
  listarPendientes();
}