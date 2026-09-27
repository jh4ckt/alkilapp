#!/usr/bin/env node
/*
 * AlkilApp - Cierre automatico de chats con acuerdo cerrado.
 *
 * Cuando el arrendatario ACEPTA una propuesta de alquiler, el chat queda en
 * estado "acuerdo_cerrado": la barra de escritura sigue activa (solo texto)
 * durante 48 horas para coordinar la entrega, y pasado ese plazo el chat se
 * cierra solo.
 *
 * Aqui se barre la coleccion "chats": los que llevan mas de HORAS_CUERDO en
 * "acuerdo_cerrado" pasan a estado "cerrado" con motivo "acuerdo_48h".
 *
 * Es idempotente: solo toca los que siguen en "acuerdo_cerrado", asi que se
 * puede ejecutar cada hora sin miedo.
 *
 * En la nube corre via Cloud Scheduler contra el panel admin
 * (GET /api/cron/cerrar-chats?clave=<ADMIN_SECRET>), que reutiliza esta
 * funcion. Requiere credenciales de Google Cloud (ver expirar.js).
 */

const PROJECT_ID = 'gen-lang-client-0040505884';
const DATABASE_ID = 'alkilappdb';
const COLECCION = 'chats';

/** Horas que un chat con acuerdo cerrado sigue escribible. */
const HORAS_CUERDO = 48;

function resolveCredentials() {
    const path = require('path');
    const fs = require('fs');
    const env = process.env.GOOGLE_APPLICATION_CREDENTIALS;
    if (env && fs.existsSync(env)) return env;
    const fallback = path.join(__dirname, '..', 'credentials', 'alkilapp-seed-sa.json');
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
 * @param {number} horas ventana de escritura tras cerrar el acuerdo
 * @returns {Promise<{revisados:number, cerrados:number, ids:string[]}>}
 */
async function cerrarChatsAcordados(db, horas = HORAS_CUERDO) {
    const corte = new Date(Date.now() - horas * 3600 * 1000);
    // igualdad + un rango en otro campo: lo resuelve con los indices de campo simple
    const snap = await db.collection(COLECCION)
        .where('estado', '==', 'acuerdo_cerrado')
        .where('acuerdo.respondidoAt', '<=', corte)
        .get();

    const ids = [];
    for (const doc of snap.docs) {
        const acuerdo = doc.get('acuerdo') || {};
        await doc.ref.set({
            estado: 'cerrado',
            cerradoMotivo: 'acuerdo_48h',
            cerradoAt: new Date(),
            cerradoPor: 'sistema',
            // El acuerdo se conserva: es el respaldo de lo pactado.
            acuerdoEstado: acuerdo.estado || 'aceptada',
            updatedAt: new Date(),
        }, { merge: true });
        ids.push(doc.id);
    }

    return { revisados: snap.size, cerrados: ids.length, ids };
}

// ---- CLI ------------------------------------------------------------------
if (require.main === module) {
    const { Firestore } = require('@google-cloud/firestore');
    const credencial = resolveCredentials();
    const db = new Firestore({
        projectId: PROJECT_ID,
        databaseId: DATABASE_ID,
        ...(credencial ? { keyFilename: credencial } : {}),
    });
    cerrarChatsAcordados(db)
        .then((r) => {
            console.log(
                `Chats revisados: ${r.revisados} | cerrados ahora: ${r.cerrados}`
            );
            if (r.ids.length) console.log('Se cerraron:', r.ids.join(', '));
        })
        .catch((err) => {
            console.error(err);
            process.exit(1);
        });
}

module.exports = { cerrarChatsAcordados, HORAS_CUERDO };
