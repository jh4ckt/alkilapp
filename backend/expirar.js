#!/usr/bin/env node
/*
 * AlkilApp — Caducidad automatica de publicaciones destacadas.
 *
 * Recorre las propiedades con `isFeatured == true`:
 *  - si `featuredUntil` ya paso -> deja de estar destacada y vuelve a ser una
 *    publicacion normal (`isFeatured=false`, `destacadoEstado='expirado'`,
 *    `destacadoDiasRestantes=0`).
 *  - si sigue vigente -> actualiza el contador `destacadoDiasRestantes`.
 *
 * Uso local:
 *   node backend/expirar.js
 *
 * En la nube corre diariamente via Cloud Scheduler contra el panel admin
 * (GET /cron/expirar?clave=<ADMIN_SECRET>), que reutiliza esta misma funcion.
 * Se puede correr las veces que haga falta: es idempotente.
 *
 * Requiere credenciales de Google Cloud (ver seed.js).
 */

const path = require('path');
const fs = require('fs');
const { Firestore } = require('@google-cloud/firestore');

const PROJECT_ID = 'gen-lang-client-0040505884';
const DATABASE_ID = 'alkilappdb';
const COLECCION = 'propiedades';

function resolveCredentials() {
  const env = process.env.GOOGLE_APPLICATION_CREDENTIALS;
  if (env && fs.existsSync(env)) return env;
  const fallback = path.join(__dirname, 'credentials', 'alkilapp-seed-sa.json');
  if (fs.existsSync(fallback)) return fallback;
  return null; // en la nube se usan las credenciales por defecto (ADC)
}

function fechaDe(valor) {
  if (!valor) return null;
  if (typeof valor.toDate === 'function') return valor.toDate();
  const d = new Date(valor);
  return isNaN(d.getTime()) ? null : d;
}

/**
 * @param {Firestore} db instancia de Firestore (se inyecta para reusar desde el panel)
 * @returns {Promise<{revisados:number, expirados:number, vigentes:number, ids:string[]}>}
 */
async function expirarDestacados(db) {
  const ahora = Date.now();
  const snap = await db.collection(COLECCION).where('isFeatured', '==', true).get();
  const ids = [];

  for (const doc of snap.docs) {
    const hasta = fechaDe(doc.get('featuredUntil'));
    // Sin fecha de caducidad no se toca (destacado permanente pedido a mano).
    if (!hasta) continue;

    if (hasta.getTime() <= ahora) {
      await doc.ref.set({
        isFeatured: false,
        destacadoEstado: 'expirado',
        destacadoDiasRestantes: 0,
        featuredUntil: null,
        updatedAt: new Date(),
      }, { merge: true });
      ids.push(doc.id);
    } else {
      const dias = Math.max(0, Math.ceil((hasta.getTime() - ahora) / 86400000));
      if (doc.get('destacadoDiasRestantes') !== dias) {
        await doc.ref.set({ destacadoDiasRestantes: dias }, { merge: true });
      }
    }
  }

  return {
    revisados: snap.size,
    expirados: ids.length,
    vigentes: snap.size - ids.length,
    ids,
  };
}

// ---- CLI ------------------------------------------------------------------
if (require.main === module) {
  const credencial = resolveCredentials();
  const db = new Firestore({
    projectId: PROJECT_ID,
    databaseId: DATABASE_ID,
    ...(credencial ? { keyFilename: credencial } : {}),
  });
  expirarDestacados(db)
    .then((r) => {
      console.log(
        `Destacados revisados: ${r.revisados} | expirados ahora: ${r.expirados} | vigentes: ${r.vigentes}`
      );
      if (r.ids.length) console.log('Caducaron:', r.ids.join(', '));
    })
    .catch((err) => {
      console.error(err);
      process.exit(1);
    });
}

module.exports = { expirarDestacados };
