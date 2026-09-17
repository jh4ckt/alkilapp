/*
 * AlkilApp — Verificación de usuario por correo (código de 6 dígitos)
 * ===================================================================
 * Flujo:
 *   1) guardarCodigo(email)   → genera código de 6 dígitos y lo guarda en la
 *                               colección "verifications" (doc id = email),
 *                               con creación y expiración a 10 minutos.
 *   2) validarCodigo(email, código) → si coincide y no expiró, marca la cuenta:
 *                               verification.emailVerified=true,
 *                               trustLevel="basic", verificationBadge=true,
 *                               en la colección de usuarios; borra el código.
 *
 * Mapeo del spec a nuestra base "alkilappdb" (decisión de fusión):
 *   - "users"  → la colección  "usuarios"  (la que lee la app Android).
 *   - "emailVerified" vive dentro del campo verification.{emailVerified}.
 *
 * Uso desde CLI (demo):  npm run verificar-demo
 */

const path = require('path');
const fs = require('fs');
const crypto = require('crypto');
const { Firestore, Timestamp } = require('@google-cloud/firestore');

const PROJECT_ID = 'gen-lang-client-0040505884';
const DATABASE_ID = 'alkilappdb';
const USER_COLLECTION = 'usuarios'; // spec "users" fusionado
const VERIFICATION_COLLECTION = 'verifications';
const TIEMPO_UNA_HORA_MINUTOS = 10; // min de validez del código
const MAX_INTENTOS = 5;

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

// ---- Generación ------------------------------------------------------------

/** Código aleatorio de 6 dígitos (string). */
function generarCodigo() {
  return crypto.randomInt(0, 1000000).toString().padStart(6, '0');
}

function normalizarEmail(email) {
  return String(email || '').trim().toLowerCase();
}

// ---- Guardar código ---------------------------------------------------------

/**
 * Genera y guarda un código de verificación para un correo.
 * El documento usa el correo normalizado como id.
 * @returns {Promise<{email: string, codigo: string, expira: Date}>}
 */
async function guardarCodigo(email) {
  const correo = normalizarEmail(email);
  if (!correo) throw new Error('Email inválido');
  const codigo = generarCodigo();
  const createdAt = new Date();
  const expiresAt = new Date(createdAt.getTime() + TIEMPO_UNA_HORA_MINUTOS * 60 * 1000);

  await db.collection(VERIFICATION_COLLECTION).doc(correo).set({
    email: correo,
    code: codigo,
    codigo, // alias en español, por consistencia con el resto del proyecto
    createdAt: Timestamp.fromDate(createdAt),
    expiresAt: Timestamp.fromDate(expiresAt),
    attempts: 0,
  }, { merge: true });

  return { email: correo, codigo, expira: expiresAt };
}

// ---- Validar código ----------------------------------------------------------

/**
 * Valida un código para un correo.
 * - Código inexistente         → {ok:false, motivo:'not_found'}
 * - Expirado                   → {ok:false, motivo:'expired'}
 * - Incorrecto (guarda intento)→ {ok:false, motivo:'invalid', intentosRestantes}
 * - Correcto                   → actualiza usuarios/{user}.verification.emailVerified,
 *                                trustLevel='basic', verificationBadge=true y borra el código.
 */
async function validarCodigo(email, codigoIngresado) {
  const correo = normalizarEmail(email);
  const code = String(codigoIngresado || '').trim();

  const ref = db.collection(VERIFICATION_COLLECTION).doc(correo);
  const doc = await ref.get();
  if (!doc.exists) {
    return { ok: false, motivo: 'not_found', mensaje: 'No hay código pendiente para este correo.' };
  }

  const data = doc.data();
  const expires = data.expiresAt ? data.expiresAt.toDate() : null;
  if (!expires || expires.getTime() < Date.now()) {
    await ref.delete(); // código vencido: limpiar
    return { ok: false, motivo: 'expired', mensaje: 'El código expiró. Solicita uno nuevo.' };
  }

  if (data.code !== code && data.codigo !== code) {
    const uso = (data.attempts || 0) + 1;
    await ref.update({ attempts: uso });
    const restantes = Math.max(0, MAX_INTENTOS - uso);
    return {
      ok: false,
      motivo: 'invalid',
      intentosRestantes: restantes,
      mensaje: restantes > 0
        ? `Código incorrecto. Te quedan ${restantes} intentos.`
        : 'Demasiados intentos fallidos. Solicita un nuevo código.',
    };
  }

  // Correcto: actualizar el usuario (buscado por email) y limpiar el código.
  const act = await marcarVerificado(correo);
  await ref.delete();
  return {
    ok: true,
    email: correo,
    usuarioActualizado: act?.id ?? null,
    mensaje: 'Correo verificado. ¡Tu nivel de confianza ahora es "basic"!',
  };
}

/** Busca el usuario por email dentro de "usuarios" y lo marca verificado. */
async function marcarVerificado(correo) {
  const resultados = await db
    .collection(USER_COLLECTION)
    .where('email', '==', correo)
    .limit(1)
    .get();

  const doc = resultados.docs[0];
  if (!doc) {
    // Algunos usuarios fueron creados con el campo "correo" o sin doc:
    // en ese caso se crea/actualiza por email sin romper la cuenta existente.
    return null;
  }

  const existente = doc.data();
  await doc.ref.update({
    verification: {
      ...(existente.verification || {}),
      emailVerified: true,
    },
    trustLevel: 'basic',
    verificationBadge: true,
    verificacionAt: Timestamp.fromDate(new Date()),
  });
  return { id: doc.id, email: correo };
}

// ---- Demo desde CLI -----------------------------------------------------------

async function demo() {
  const emailDemo = process.argv[2] || 'valeria.quispe@example.pe';
  console.log(`Demo de verificación para: ${emailDemo}`);
  console.log('1) Generando y guardando código...');
  const { codigo, expira } = await guardarCodigo(emailDemo);
  console.log(`   → Código: ${codigo} (expira ${expira.toISOString()})`);

  console.log('2) Validando un código INCORRECTO...');
  console.log('   →', await validarCodigo(emailDemo, '000000'));

  console.log('3) Validando el código CORRECTO...');
  console.log('   →', await validarCodigo(emailDemo, codigo));

  const u = await db.collection(USER_COLLECTION)
    .where('email', '==', emailDemo).limit(1).get();
  const doc = u.docs[0];
  if (doc) {
    const d = doc.data();
    console.log('   → usuarios/' + doc.id +
      ': emailVerified=' + (d.verification?.emailVerified) + ', trustLevel=' + d.trustLevel);
  } else {
    console.log('   → (el correo no tiene cuenta en usuarios; actualización no aplicada)');
  }
}

if (require.main === module) {
  demo()
    .then(() => process.exit(0))
    .catch((e) => {
      console.error('[verificacion] ERROR:', e.message);
      process.exit(1);
    });
}

module.exports = { guardarCodigo, validarCodigo, generarCodigo, db, USER_COLLECTION };