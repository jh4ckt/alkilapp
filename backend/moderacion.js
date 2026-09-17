/*
 * AlkilApp — Sistema de denuncias y moderación automática
 * =======================================================
 * Spec: cuando se crea un documento en "reports", se incrementa
 * reportsCount del inmueble; al llegar a 3, su estado cambia de
 * "active"/"disponible" a "under_review" (queda oculto del listado público).
 *
 * En producción este flujo corre como un Cloud Function (trigger onCreate
 * de "reports"). Este módulo expone la misma lógica como funciones Node reutilizables:
 *   - registrarDenuncia({...})          crea el documento en "reports".
 *   - aplicarDenuncia(reportRef)        procesa UN reporte (transacción).
 *   - iniciarEscuchaDenuncias()         listener de desarrollo: reacciona
 *                                       automáticamente a los "added".
 *
 * Mapeo del spec a nuestra base "alkilappdb":
 *   - "listings" → colección "propiedades" (la que usa la app Android).
 *   - "status"   → campo "estado" de "propiedades" ("disponible" → "under_review").
 *
 * Uso desde CLI (demo):  npm run reportes-demo
 */

const path = require('path');
const fs = require('fs');
const { Firestore } = require('@google-cloud/firestore');

const PROJECT_ID = 'gen-lang-client-0040505884';
const DATABASE_ID = 'alkilappdb';
const REPORT_COLLECTION = 'reports'; // spec "reports"
const LISTING_COLLECTION = 'propiedades'; // spec "listings" fusionado
const UMBRAL_DENUNCIAS = 3;
const ESTADO_DISPONIBLE = 'disponible';
const ESTADO_EN_REVISION = 'under_review';

function resolveCredentials() {
  const env = process.env.GOOGLE_APPLICATION_CREDENTIALS;
  if (env && fs.existsSync(env)) return env;
  const fallback = path.join(__dirname, 'credentials', 'alkilapp-seed-sa.json');
  if (fs.existsSync(fallback)) return fallback;
  throw new Error('No credentials found (GOOGLE_APPLICATION_CREDENTIALS o backend/credentials/).');
}

const db = new Firestore({
  projectId: PROJECT_ID,
  databaseId: DATABASE_ID,
  keyFilename: resolveCredentials(),
});

const idsProcesados = new Set(); // idempotencia del listener en este proceso

// ---- Registrar denuncia -------------------------------------------------------

/**
 * Crea un documento en "reports".
 * El incremento de reportsCount lo hace aplicarDenuncia() (o el listener),
 * replicando el trigger onCreate del spec.
 */
async function registrarDenuncia({ listingId, reporterId, motivo, detalle }) {
  if (!listingId) throw new Error('listingId requerido');
  if (!reporterId) throw new Error('reporterId requerido');
  if (!motivo) throw new Error('motivo requerido');

  const ref = db.collection(REPORT_COLLECTION).doc();
  await ref.set({
    listingId,
    reporterId,
    motivo: String(motivo),
    detalle: String(detalle || ''),
    chapter: 'reports', // etiqueta de dominio del spec
    createdAt: require('@google-cloud/firestore').Timestamp.now(),
  });
  return { id: ref.id, listingId, motivo };
}

// ---- Procesar una denuncia (transacción) ----------------------------------------

/** Incrementa reportsCount del inmueble; al llegar al umbral lo pasa a under_review. */
async function aplicarDenuncia(reportRef) {
  return db.runTransaction(async (tx) => {
    const reportSnap = await tx.get(reportRef);
    if (!reportSnap.exists) return { ok: false, motivo: 'report_no_exists' };
    const report = reportSnap.data();
    if (!report.listingId) return { ok: false, motivo: 'sin_listing' };

    const listingRef = db.collection(LISTING_COLLECTION).doc(report.listingId);
    const listingSnap = await tx.get(listingRef);
    if (!listingSnap.exists) return { ok: false, motivo: 'listing_no_exists' };
    const listing = listingSnap.data();

    const reportesNuevo = (Number(listing.reportsCount) || 0) + 1;
    const updates = {
      reportsCount: reportesNuevo,
      moderadoAt: require('@google-cloud/firestore').Timestamp.now(),
    };

    if (reportesNuevo >= UMBRAL_DENUNCIAS && listing.estado === ESTADO_DISPONIBLE) {
      updates.estado = ESTADO_EN_REVISION;
    }

    tx.update(listingRef, updates);
    return {
      ok: true,
      listingId: report.listingId,
      reportsCount: reportesNuevo,
      estado: updates.estado || listing.estado,
      umbral: UMBRAL_DENUNCIAS,
    };
  });
}

// ---- Listener de desarrollo -----------------------------------------------------

/** Reacciona automáticamente a los reports creados (equivale al Cloud Function trigger). */
function iniciarEscuchaDenuncias() {
  return db.collection(REPORT_COLLECTION).onSnapshot(
    (snapshot) => {
      snapshot.docChanges().forEach((cambio) => {
        if (cambio.type !== 'added') return;
        const id = cambio.doc.id;
        if (idsProcesados.has(id)) return;
        idsProcesados.add(id);
        aplicarDenuncia(cambio.doc.ref)
          .then((r) => console.log(`[moderacion] reporte ${id} →`, JSON.stringify(r)))
          .catch((e) => console.error(`[moderacion] fallo al procesar ${id}:`, e.message));
      });
    },
    (err) => console.error('[moderacion] listener error:', err.message)
  );
}

// ---- Utilidades de consulta ------------------------------------------------------

async function obtenerEstado(listingId) {
  const snap = await db.collection(LISTING_COLLECTION).doc(listingId).get();
  if (!snap.exists) return null;
  const d = snap.data();
  return { estado: d.estado, reportsCount: Number(d.reportsCount) || 0 };
}

// ---- Demo desde CLI ---------------------------------------------------------------

async function demo() {
  const listingId = process.argv[2] || 'seed-l-depa-sanmiguel';
  const reporter = 'u-inquilino-1';
  console.log(`Demo de moderación sobre: ${listingId}\n`);

  console.log('Antes:', await obtenerEstado(listingId));

  const detener = iniciarEscuchaDenuncias();
  await new Promise((r) => setTimeout(r, 700)); // arranca el listener

  for (let i = 1; i <= UMBRAL_DENUNCIAS; i++) {
    const reporte = await registrarDenuncia({
      listingId,
      reporterId: reporter,
      motivo: 'publicidad engañosa',
      detalle: `Denuncia de prueba #${i}`,
    });
    console.log(`  → reporte ${i}/${UMBRAL_DENUNCIAS} creado (${reporte.id})`);
    await new Promise((r) => setTimeout(r, 900)); // deja que el listener procese
  }

  await new Promise((r) => setTimeout(r, 700));
  detener();
  console.log('\nDespués:', await obtenerEstado(listingId));
  console.log('\nResultado: el inmueble quedó', await (async () => {
    const e = await obtenerEstado(listingId);
    return e.estado === ESTADO_EN_REVISION
      ? 'oculto del listado público (under_review) ✔'
      : 'en estado ' + e.estado + ' (aún no alcanzó el umbral)';
  })());
}

if (require.main === module) {
  demo()
    .then(() => process.exit(0))
    .catch((e) => {
      console.error('[moderacion] ERROR:', e.message);
      process.exit(1);
    });
}

module.exports = {
  registrarDenuncia,
  aplicarDenuncia,
  iniciarEscuchaDenuncias,
  obtenerEstado,
  db,
  UMBRAL_DENUNCIAS,
  ESTADO_EN_REVISION,
  REPORT_COLLECTION,
};