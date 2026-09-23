/*
 * AlkilApp — Consultas de catálogo (backend / Node.js)
 * =====================================================
 *
 * Ejemplo de consulta de "monetización": propiedades activas de Lima con los
 * anuncios DESTACADOS primero.
 *
 *   npm run query-demo
 *
 * Nota de diseño: Firestore no permite ORDER BY por booleano + dos índices
 * distintos (destacado > ahora y createdAt), por eso se trae el subconjunto
 * filtrado y se ordena en memoria (subconjunto pequeño, decenas/centenas).
 * Si el catálogo creciera mucho, la alternativa es mantener un campo
 * `featuredRank` (0=normal, 1=destacado) para poder usar orderBy del lado
 * servidor con un índice compuesto.
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
    'No credentials. Set GOOGLE_APPLICATION_CREDENTIALS or restore ' +
      'backend/credentials/alkilapp-seed-sa.json'
  );
}

const db = new Firestore({
  projectId: PROJECT_ID,
  databaseId: DATABASE_ID,
  keyFilename: resolveCredentials(),
});

function millis(v) {
  if (!v) return 0;
  if (typeof v.toDate === 'function') return v.toDate().getTime();
  return Number(v) || 0;
}

function esDestacado(d, ahora) {
  return d.isFeatured === true && millis(d.featuredUntil) > ahora;
}

/**
 * Propiedades activas de Lima, DESTACADAS primero.
 *
 * @param {Object} opts
 * @param {number} [opts.limite=50]   máx. resultados totales devueltos
 * @param {boolean} [opts.detallado=false]  incluye agrupación destacadas/normales
 * @returns {Promise<Object[]> | Promise<{destacadas, normales, total}>}
 */
async function obtenerPropiedadesActivasLima({ limite = 50, detallado = false } = {}) {
  const ahora = Date.now();

  const snap = await db
    .collection('propiedades')
    .where('ciudad', '==', 'Lima')
    .where('estado', '==', 'disponible')
    .limit(500)
    .get();

  const docs = snap.docs.map((d) => ({ id: d.id, ...d.data() }));

  const destacadas = [];
  const normales = [];
  for (const d of docs) {
    (esDestacado(d, ahora) ? destacadas : normales).push(d);
  }

  const porCreacionDesc = (a, b) => millis(b.createdAt) - millis(a.createdAt);

  destacadas.sort(porCreacionDesc);
  normales.sort(porCreacionDesc);

  const total = destacadas.length + normales.length;
  const ordenadas = [...destacadas, ...normales].slice(0, limite);

  return detallado
    ? {
        destacadas: destacadas.slice(0, limite),
        normales: normales.slice(0, limite),
        total,
        ordenadas,
      }
    : ordenadas;
}

// ---- Demo desde CLI ---------------------------------------------------------
async function demo() {
  const { destacadas, normales, total, ordenadas } = await obtenerPropiedadesActivasLima({
    detallado: true,
  });
  console.log(`Lima activas: ${total} total | ${destacadas.length} destacadas | ${normales.length} normales`);
  console.log('\nORDEN FINAL (destacadas primero):');
  for (const p of ordenadas) {
    const tag = esDestacado(p, Date.now()) ? '⭐ DESTACADA' : '   normal';
    const distrito = p.barrio || p.district || '?';
    const precio = `${p.currency || 'PEN'}${p.price ?? p.precio ?? '?'}`;
    console.log(`  ${tag}  [${distrito}] ${p.titulo || p.title} — ${precio}`);
  }
}

if (require.main === module) {
  demo()
    .then(() => process.exit(0))
    .catch((e) => {
      console.error('[queries] ERROR:', e.message);
      process.exit(1);
    });
}

module.exports = { obtenerPropiedadesActivasLima };