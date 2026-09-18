#!/usr/bin/env node
/*
 * AlkilApp — Flujo de "Destacar publicacion" (aprobacion + vigencia)
 *
 * Listo para conectar con Google Play Billing: cuando el pago se active, el
 * backend debe verificar el purchaseToken / la orden antes de llamar a
 * `aprobarDestacado()`. Por ahora la aprobacion es manual via CLI.
 *
 * Uso:
 *   node backend/destacar.js                     -> lista solicitudes pendientes
 *   node backend/destacar.js --id <id> [--dias 30]  -> aprueba y destaca
 *   node backend/destacar.js --rechazar <id>        -> rechaza la solicitud
 *   node backend/destacar.js --quitar <id>          -> quita el destacado
 *
 * Requiere credenciales de Google Cloud (ver seed.js).
 */

const path = require('path');
const fs = require('fs');
const { Firestore } = require('@google-cloud/firestore');

const PROJECT_ID = 'gen-lang-client-0040505884';
const DATABASE_ID = 'alkilappdb';
const DIAS_POR_DEFECTO = 30;

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

function fecha(valor) {
  if (!valor) return '';
  if (typeof valor.toDate === 'function') return valor.toDate().toISOString();
  return String(valor);
}

async function listarSolicitudes() {
  const snap = await db.collection('propiedades')
    .where('solicitudDestacar', '==', true)
    .get();

  if (snap.empty) {
    console.log('No hay solicitudes de destacado pendientes.');
    return;
  }
  console.log(`Solicitudes de destacado pendientes (${snap.size}):`);
  snap.forEach(doc => {
    const d = doc.data();
    console.log(
      `- ${doc.id}: titulo="${d.titulo || ''}", dueño=${d.idPropietario || ''}, ` +
        `solicitado=${fecha(d.solicitudDestacarEn)}`
    );
  });
  console.log('\nAprobar: node backend/destacar.js --id <id> [--dias 30]');
}

/** Aprueba el destacado. `pagoVerificado` debe venir de Play Billing cuando se active.
 *  Si no se pasa `dias`, se respeta lo que el usuario pidio desde la app (7/15/30). */
async function aprobarDestacado(id, dias = null, pagoVerificado = false) {
  const ref = db.collection('propiedades').doc(id);
  const snap = await ref.get();
  if (!snap.exists) {
    console.log(`Propiedad ${id} no encontrada.`);
    return;
  }
  const data = snap.data();
  if (data.solicitudDestacar !== true && data.isFeatured !== true) {
    console.log(`La propiedad ${id} no tiene una solicitud pendiente.`);
    return;
  }

  const pedidos = Number(data.destacadoDias);
  const total = Number(dias) > 0 ? Number(dias) : (pedidos > 0 ? pedidos : DIAS_POR_DEFECTO);
  const hasta = new Date(Date.now() + total * 24 * 60 * 60 * 1000);
  await ref.update({
    isFeatured: true,
    featuredUntil: hasta,
    destacadoDias: total,
    // Contador que el backend mantiene al dia (ver expirar.js): dias que faltan.
    destacadoDiasRestantes: total,
    solicitudDestacar: false,
    destacadoEstado: 'aprobado',
    destacadoPagoVerificado: pagoVerificado,
    destacadoAprobadoEn: new Date(),
    destacadoAprobadoPor: 'admin-cli',
    updatedAt: new Date(),
  });
  console.log(
    `Propiedad ${id} destacada por ${total} dias (hasta ${hasta.toISOString()}).`
  );
}

async function rechazarDestacado(id, motivo = 'no especificado') {
  const ref = db.collection('propiedades').doc(id);
  const snap = await ref.get();
  if (!snap.exists) {
    console.log(`Propiedad ${id} no encontrada.`);
    return;
  }
  await ref.update({
    solicitudDestacar: false,
    destacadoEstado: 'rechazado',
    destacadoMotivo: motivo,
    updatedAt: new Date(),
  });
  console.log(`Solicitud de destacado de ${id} rechazada (${motivo}).`);
}

async function quitarDestacado(id) {
  const ref = db.collection('propiedades').doc(id);
  await ref.update({
    isFeatured: false,
    featuredUntil: null,
    destacadoDiasRestantes: 0,
    destacadoEstado: 'expirado',
    updatedAt: new Date(),
  });
  console.log(`Destacado de ${id} retirado.`);
}

// ---- CLI ------------------------------------------------------------------
const args = process.argv.slice(2);
const cmd = args[0];

function valorDe(flag) {
  const i = args.indexOf(flag);
  return i >= 0 ? args[i + 1] : null;
}

if (cmd === '--id' || cmd === '-i') {
  const id = args[1];
  if (!id) { console.error('Falta el ID de la propiedad.'); process.exit(1); }
  const dias = valorDe('--dias');
  aprobarDestacado(id, dias).catch(err => { console.error(err); process.exit(1); });
} else if (cmd === '--rechazar') {
  const id = args[1];
  if (!id) { console.error('Falta el ID de la propiedad.'); process.exit(1); }
  rechazarDestacado(id, valorDe('--motivo') || 'no especificado')
    .catch(err => { console.error(err); process.exit(1); });
} else if (cmd === '--quitar') {
  const id = args[1];
  if (!id) { console.error('Falta el ID de la propiedad.'); process.exit(1); }
  quitarDestacado(id).catch(err => { console.error(err); process.exit(1); });
} else {
  listarSolicitudes().catch(err => { console.error(err); process.exit(1); });
}

module.exports = { aprobarDestacado, rechazarDestacado };
