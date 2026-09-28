/*
 * Pruebas de las reglas de AlkilApp (chat de alquiler) contra el EMULADOR de
 * Firestore, con datos sembrados. No toca la base real (alkilappdb).
 *
 * Se ejecuta asi (el emulador se levanta y se apaga solo):
 *
 *   npm run reglas:test
 *
 * Por que el emulador y no la API de test de rules: esa API no permite sembrar
 * documentos de contexto, asi que todo lo que usa get()/exists() sobre otro
 * documento (la excepcion "pausada" tras un acuerdo, la pertenencia al chat de
 * un mensaje) no se puede evaluatesin datos reales sembrados.
 */
const fs = require('fs');
const path = require('path');
const {
    initializeTestEnvironment,
} = require('@firebase/rules-unit-testing');
const {
    doc, setDoc, updateDoc, addDoc, deleteDoc, collection, getDoc, getDocs, serverTimestamp,
} = require('firebase/firestore');

const PROJECT = process.env.GCLOUD_PROJECT || 'gen-lang-client-0040505884';
const RULES = path.join(__dirname, 'firestore.rules');

const OWNER = 'dueno-1';
const RENTER = 'arrendatario-1';
const AJENO = 'ajeno-1';
const LISTING = 'inm-1';
const CHAT = 'inm-1-renter';
const MSGS = `chats/${CHAT}/messages`;

let testEnv = null;
let fallos = 0;
let total = 0;

/* ------------------------------------------------------------------ helpers */

// Un cliente por usuario, reutilizado. Crear un cliente nuevo en cada operacion
// hace que el emulador evalue los get() de las reglas contra un estado viejo y
// las escrituras "permitidas" salgan como PERMISSION_DENIED (es un quirk del
// emulador, no de las reglas: en produccion Firestore no pasa).
const _dbs = {};
const dbDe = (uid) => (_dbs[uid] = _dbs[uid]
    || testEnv.authenticatedContext(uid, { email: `${uid}@test.local` }).firestore());

/** Siembra documentos saltandose las reglas (como haria la service account). */
async function sembrar(fn) {
    await testEnv.withSecurityRulesDisabled(async ctx => fn(ctx.firestore()));
    // El emulador puede evaluar el get() de las reglas contra un estado viejo
    // (la escritura recien sembrada todavia no se ve) y convertir en
    // PERMISSION_DENIED escrituras que las reglas SI permiten. Un round-trip de
    // lectura sobre lo recien sembrado fuerza la sincronizacion.
    // OJO: withSecurityRulesDisabled NO devuelve el valor del callback, asi que
    // el resultado de la lectura se guarda en una variable externa.
    let visto = false;
    await testEnv.withSecurityRulesDisabled(async ctx => {
        const c = await getDoc(doc(ctx.firestore(), 'chats', CHAT));
        visto = c.exists();
    });
    if (!visto) throw new Error(`La siembra no quedo visible en el emulador (chats/${CHAT})`);
}

const chatBase = (extra = {}) => ({
    participants: [OWNER, RENTER], listingId: LISTING, listingTitle: 'Depto QA',
    estado: 'abierto', ...extra,
});
const propBase = (extra = {}) => ({
    idPropietario: OWNER, titulo: 'Depto QA', estado: 'disponible', ...extra,
});
const propuesta = (extra = {}) => ({
    senderId: OWNER, text: 'Propuesta de alquiler', tipo: 'propuesta',
    propuestoPor: OWNER, propuestaId: 'p1', monto: 1500, moneda: 'PEN',
    propuestaEstado: 'pendiente', ...extra,
});
// Cita de visita: la agenda el INTERESADO (no el dueno) y nace "pendiente".
const cita = (extra = {}) => ({
    senderId: RENTER, text: 'Quisiera visitarlo', tipo: 'cita',
    citaId: 'c1', solicitadoPor: RENTER, fecha: '15/09/2026', hora: '10:30',
    citaEstado: 'pendiente', ...extra,
});

/*
 * check() propio en vez de assertSucceeds/assertFails: con esas helpers la
 * libreria se comporta distinto segas se le pase la funcion o la promesa ya
 * empezada (assertFails ignora el thunk, assertSucceeds pierde la sincronizacion
 * con la promesa), y las escrituras "permitidas" salian como PERMISSION_DENIED.
 * Aqui la operacion SIEMPRE se ejecuta y lo que se mira es si lanza o no.
 */
async function check(nombre, esperado, fn) {
    total++;
    let error = null;
    try {
        await fn();
    } catch (e) {
        error = e;
    }
    const ok = esperado === 'ALLOW' ? error === null : error !== null;
    if (ok) {
        console.log(`  OK    ${nombre}`);
        return;
    }
    fallos++;
    const detalle = error
        ? String(error.message || error).split('\n').slice(0, 2).join(' | ').replace(/\s+/g, ' ').slice(0, 170)
        : 'se esperaba DENY pero la operacion fue permitida';
    console.log(`  FALLA ${nombre}\n         ${detalle}`);
}

const crearMensaje = (db, id, data) => setDoc(doc(db, ...MSGS.split('/'), id), data);
const crearMsg = (uid, id, data) => crearMensaje(dbDe(uid), id, data);
const updMsg = (uid, id, data) => updateDoc(doc(dbDe(uid), ...MSGS.split('/'), id), data);
const nuevoMsg = (uid, data) => addDoc(collection(dbDe(uid), ...MSGS.split('/')), data);
const updChat = (uid, data) => updateDoc(doc(dbDe(uid), 'chats', CHAT), data);
const updProp = (uid, data) => updateDoc(doc(dbDe(uid), 'propiedades', LISTING), data);

/* -------------------------------------------------------------------- fases */

async function fase1_propuestas() {
    console.log('\n1) Propuestas de acuerdo: solo el dueno propone, el otro responde');
    await testEnv.clearFirestore();
    await sembrar(async db => {
        await setDoc(doc(db, 'chats', CHAT), chatBase());
        await setDoc(doc(db, 'propiedades', LISTING), propBase());
    });

    await check('dueno escribe un texto', 'ALLOW', () => nuevoMsg(OWNER, { senderId: OWNER, text: 'hola', tipo: 'texto' }));
    await check('dueno crea una propuesta', 'ALLOW', () => crearMsg(OWNER, 'p1', propuesta()));
    await check('arrendatario NO puede proponer', 'DENY', () => crearMsg(RENTER, 'p2', propuesta({ senderId: RENTER, propuestoPor: RENTER, propuestaId: 'p2' })));
    await check('arrendatario ACEPTA la propuesta', 'ALLOW', () => updMsg(RENTER, 'p1', { propuestaEstado: 'aceptada', respondidoPor: RENTER, respondidoAt: serverTimestamp() }));
    await check('arrendatario RECHAZA la propuesta', 'ALLOW', () => updMsg(RENTER, 'p1', { propuestaEstado: 'rechazada' }));
    await check('no se puede tocar el MONTO', 'DENY', () => updMsg(RENTER, 'p1', { monto: 9999 }));
    await check('no se puede reescribir el TEXTO', 'DENY', () => updMsg(RENTER, 'p1', { text: 'lo cambio a mano' }));
    await check('no se puede cambiar el PROPUESTO POR', 'DENY', () => updMsg(RENTER, 'p1', { propuestoPor: RENTER }));
    await check('texto vacio', 'DENY', () => nuevoMsg(OWNER, { senderId: OWNER, text: '', tipo: 'texto' }));
    await check('texto de mas de 1000', 'DENY', () => nuevoMsg(OWNER, { text: 'a'.repeat(1001), tipo: 'texto' }));
    await check('texto sin tipo (mensajes antiguos)', 'ALLOW', () => nuevoMsg(OWNER, { senderId: OWNER, text: 'mensaje viejo' }));
    await check('autor spoofeado (senderId de otro)', 'DENY', () => nuevoMsg(OWNER, { senderId: RENTER, text: 'hola', tipo: 'texto' }));
    await check('tipo inventado', 'DENY', () => nuevoMsg(OWNER, { senderId: OWNER, text: 'x', tipo: 'hack' }));
    await check('mensaje de sistema', 'ALLOW', () => nuevoMsg(OWNER, { senderId: OWNER, text: 'aviso', tipo: 'sistema' }));
    await check('un tercero no escribe en el chat', 'DENY', () => nuevoMsg(AJENO, { senderId: AJENO, text: 'hola', tipo: 'texto' }));
    await check('un tercero no responde la propuesta', 'DENY', () => updMsg(AJENO, 'p1', { propuestaEstado: 'aceptada' }));
}

async function fase2_autoaceptacion() {
    console.log('\n2) El dueno no acepta su propia propuesta');
    await sembrar(async db => {
        await setDoc(doc(db, 'chats', CHAT, 'messages', 'p9'), propuesta({ propuestaId: 'p9' }));
    });
    await check('dueno se auto-acepta', 'DENY', () => updMsg(OWNER, 'p9', { propuestaEstado: 'aceptada', respondidoPor: OWNER }));
    await check('arrendatario si acepta esa propuesta', 'ALLOW', () => updMsg(RENTER, 'p9', { propuestaEstado: 'aceptada', respondidoPor: RENTER }));
}

async function fase3_chats() {
    console.log('\n3) Estados del chat y escritura segun el estado');
    await testEnv.clearFirestore();
    await sembrar(async db => setDoc(doc(db, 'chats', CHAT), chatBase()));

    await check('crear chat con 2 participantes', 'ALLOW', () => setDoc(doc(dbDe(OWNER), 'chats', 'otro-chat'), chatBase()));
    await check('crear chat con 1 solo participante', 'DENY', () => setDoc(doc(dbDe(OWNER), 'chats', 'chat-malo'), { participants: [OWNER] }));
    await check('crear chat sin participar en el', 'DENY', () => setDoc(doc(dbDe(OWNER), 'chats', 'chat-malo2'), { participants: [RENTER, AJENO] }));
    await check('cambiar estado a acuerdo_cerrado', 'ALLOW', () => updChat(OWNER, { estado: 'acuerdo_cerrado' }));
    await check('estado inventado', 'DENY', () => updChat(OWNER, { estado: 'inventado' }));
    await check('en acuerdo_cerrado se sigue escribiendo', 'ALLOW', () => nuevoMsg(RENTER, { senderId: RENTER, text: 'gracias', tipo: 'texto' }));
    await check('cerrar el chat', 'ALLOW', () => updChat(OWNER, { estado: 'cerrado' }));
    await check('en cerrado NO se escribe texto', 'DENY', () => nuevoMsg(RENTER, { senderId: RENTER, text: 'hola?', tipo: 'texto' }));
    await check('en cerrado el aviso de sistema SI entra', 'ALLOW', () => nuevoMsg(OWNER, { senderId: OWNER, text: 'El chat se cerro a las 48h', tipo: 'sistema' }));
    await check('reabrir a abierto', 'ALLOW', () => updChat(OWNER, { estado: 'abierto' }));
    await check('un tercero no cambia el estado', 'DENY', () => updChat(AJENO, { estado: 'cerrado' }));
    await check('un tercero no puede colarse en participants', 'DENY', () => updateDoc(doc(dbDe(OWNER), 'chats', CHAT), { participants: [OWNER, RENTER, AJENO] }));
}

async function fase4_pausa() {
    console.log('\n4) La propiedad se pausa SOLO con un acuerdo ya aceptado');
    await testEnv.clearFirestore();
    await sembrar(async db => {
        await setDoc(doc(db, 'chats', CHAT), chatBase());
        await setDoc(doc(db, 'propiedades', LISTING), propBase());
    });

    await check('dueno registra el acuerdo pendiente', 'ALLOW', () => updProp(OWNER, { acuerdoPendiente: { chatId: CHAT, propuestaId: 'p1' } }));
    await check('sin acuerdo aceptado NO puede pausar', 'DENY', () => updProp(RENTER, { estado: 'pausada' }));
    await check('un tercero tampoco', 'DENY', () => updProp(AJENO, { estado: 'pausada' }));
    await check('el dueno puede finalizar cuando quiera', 'ALLOW', () => updProp(OWNER, { estado: 'finalizado' }));

    await sembrar(async db => updateDoc(doc(db, 'chats', CHAT), {
        acuerdo: { estado: 'aceptada', propuestaId: 'p1', monto: 1500 }, estado: 'acuerdo_cerrado',
    }));
    await check('con acuerdo aceptado SI puede pausar', 'ALLOW', () => updProp(RENTER, { estado: 'pausada' }));
    await check('pausar + tocar otro campo a la vez', 'DENY', () => updProp(RENTER, { estado: 'disponible', titulo: 'miximo' }));
}

async function fase5_inmueble_finalizado() {
    console.log('\n5) Inmueble FINALIZADO cierra el chat (regla existente)');
    await testEnv.clearFirestore();
    await sembrar(async db => {
        await setDoc(doc(db, 'chats', CHAT), chatBase());
        await setDoc(doc(db, 'propiedades', LISTING), propBase({ estado: 'finalizado' }));
    });

    await check('no se escribe si el inmueble esta finalizado', 'DENY', () => nuevoMsg(OWNER, { senderId: OWNER, text: 'hola', tipo: 'texto' }));
    await check('el aviso de sistema tampoco (inmueble cerrado)', 'DENY', () => nuevoMsg(OWNER, { senderId: OWNER, text: 'aviso', tipo: 'sistema' }));
    await sembrar(async db => updateDoc(doc(db, 'propiedades', LISTING), { estado: 'pausada' }));
    await check('vuelve a escribirse si no esta finalizado', 'ALLOW', () => nuevoMsg(OWNER, { senderId: OWNER, text: 'hola', tipo: 'texto' }));
}

async function fase6_denuncias() {
    console.log('\n6) Denuncias de un chat');
    await check('reporta con listingId y motivo', 'ALLOW', () => setDoc(doc(dbDe(RENTER), 'reports', 'r1'), {
        reporterId: RENTER, listingId: LISTING, chatId: CHAT, tipo: 'usuario', motivo: 'prueba', detalle: '',
    }));
    await check('denuncia sin motivo', 'DENY', () => setDoc(doc(dbDe(RENTER), 'reports', 'r2'), {
        reporterId: RENTER, listingId: LISTING, chatId: CHAT, tipo: 'usuario',
    }));
    await check('denuncia en nombre de otro', 'DENY', () => setDoc(doc(dbDe(RENTER), 'reports', 'r3'), {
        reporterId: AJENO, listingId: LISTING, motivo: 'prueba',
    }));
}

async function fase7_citas() {
    console.log('\n7) Citas de visita: las agenda el interesado y las confirma el dueno');
    await testEnv.clearFirestore();
    await sembrar(async db => {
        await setDoc(doc(db, 'chats', CHAT), chatBase());
        await setDoc(doc(db, 'propiedades', LISTING), propBase());
        await setDoc(doc(db, 'chats', CHAT, 'messages', 'c1'), cita({ citaId: 'c1' }));
        await setDoc(doc(db, 'chats', CHAT, 'messages', 'c9'),
            cita({ citaId: 'c9', citaEstado: 'aceptada', respondidoPor: OWNER }));
    });

    const sinFecha = cita({ citaId: 'c4' }); delete sinFecha.fecha;
    const sinHora = cita({ citaId: 'c5' }); delete sinHora.hora;

    await check('el interesado agenda la visita', 'ALLOW', () => crearMsg(RENTER, 'c2', cita({ citaId: 'c2' })));
    await check('el dueno NO agenda visita', 'DENY', () => crearMsg(OWNER, 'c3', cita({ citaId: 'c3', senderId: OWNER, solicitadoPor: OWNER })));
    await check('cita en nombre de otro', 'DENY', () => crearMsg(RENTER, 'c6', cita({ citaId: 'c6', solicitadoPor: OWNER })));
    await check('cita sin fecha', 'DENY', () => crearMsg(RENTER, 'c4', sinFecha));
    await check('cita sin hora', 'DENY', () => crearMsg(RENTER, 'c5', sinHora));
    await check('citaId que no es el del mensaje', 'DENY', () => crearMsg(RENTER, 'c7', cita({ citaId: 'otra-cosa' })));
    await check('cita ya aceptada al crearse', 'DENY', () => crearMsg(RENTER, 'c8', cita({ citaId: 'c8', citaEstado: 'aceptada' })));
    await check('el dueno ACEPTA la visita', 'ALLOW', () => updMsg(OWNER, 'c1', { citaEstado: 'aceptada', respondidoPor: OWNER, respondidoAt: serverTimestamp() }));
    await check('el dueno RECHAZA la visita', 'ALLOW', () => crearMsg(RENTER, 'c10', cita({ citaId: 'c10' }))
        .then(() => updMsg(OWNER, 'c10', { citaEstado: 'rechazada', respondidoPor: OWNER })));
    await check('el solicitante NO se auto-confirma', 'DENY', () => crearMsg(RENTER, 'c11', cita({ citaId: 'c11' }))
        .then(() => updMsg(RENTER, 'c11', { citaEstado: 'aceptada', respondidoPor: RENTER })));
    await check('un tercero NO responde', 'DENY', () => updMsg(AJENO, 'c1', { citaEstado: 'aceptada' }));
    await check('no se puede cambiar la FECHA', 'DENY', () => updMsg(OWNER, 'c1', { citaEstado: 'aceptada', fecha: '01/01/2030' }));
    await check('no se puede cambiar el TEXTO', 'DENY', () => updMsg(OWNER, 'c1', { text: ' manipulado' }));
    await check('no se puede re-responder una cita ya aceptada', 'DENY', () => updMsg(OWNER, 'c9', { citaEstado: 'rechazada', respondidoPor: OWNER }));
    await check('el interesado registra la cita pendiente en el chat', 'ALLOW', () => updChat(RENTER, {
        citaPendiente: { citaId: 'c1', solicitadoPor: RENTER, fecha: '15/09/2026', hora: '10:30', estado: 'pendiente' },
    }));
    await check('el dueno registra la visita confirmada en el chat', 'ALLOW', () => updChat(OWNER, {
        cita: { estado: 'aceptada', citaId: 'c1', fecha: '15/09/2026', hora: '10:30', aceptadoPor: OWNER },
    }));
    await check('el dueno cambia el estado del chat a cerrado', 'ALLOW', () => updChat(OWNER, { estado: 'cerrado' }));
    await check('en chat cerrado NO se agenda visita', 'DENY', () => crearMsg(RENTER, 'c12', cita({ citaId: 'c12' })));

    await sembrar(async db => updateDoc(doc(db, 'chats', CHAT), { estado: 'abierto' }));
    await sembrar(async db => updateDoc(doc(db, 'propiedades', LISTING), { estado: 'finalizado' }));
    await check('no se agenda visita si el inmueble esta alquilado', 'DENY', () => crearMsg(RENTER, 'c13', cita({ citaId: 'c13' })));
}

async function fase8_soft_delete() {
    console.log('\n8) Ocultar el chat (soft delete) y prohibido borrar de verdad');
    await testEnv.clearFirestore();
    await sembrar(async db => {
        await setDoc(doc(db, 'chats', CHAT), chatBase());
        await setDoc(doc(db, 'propiedades', LISTING), propBase());
        await setDoc(doc(db, 'chats', CHAT, 'messages', 'm1'), { senderId: RENTER, text: 'hola', tipo: 'texto' });
    });

    await check('el interesado oculta el chat (su propio uid)', 'ALLOW', () => updChat(RENTER, { deletedForUsers: [RENTER] }));
    await check('el dueno tambien puede ocultarlo', 'ALLOW', () => updChat(OWNER, { deletedForUsers: [RENTER, OWNER] }));
    await check('NO se puede quitar un uid de la lista', 'DENY', () => setDoc(doc(dbDe(OWNER), 'chats', CHAT), { deletedForUsers: [RENTER] }, { merge: true }));
    await check('NO se puede ocultar el chat de otro', 'DENY', () => setDoc(doc(dbDe(OWNER), 'chats', CHAT), { deletedForUsers: [RENTER, OWNER, AJENO] }, { merge: true }));
    await check('un tercero no toca deletedForUsers', 'DENY', () => updChat(AJENO, { deletedForUsers: [RENTER, OWNER, AJENO] }));
    await check('un oculto puede seguir escribiendo en su chat', 'ALLOW', () => nuevoMsg(RENTER, { senderId: RENTER, text: 'otro mensaje', tipo: 'texto' }));
    await check('el historial de un chat oculto sigue legible', 'ALLOW', () => getDocs(collection(dbDe(RENTER), ...MSGS.split('/'))));
    await check('NO se puede BORRAR el documento del chat', 'DENY', () => deleteDoc(doc(dbDe(OWNER), 'chats', CHAT)));
    await check('NO se puede BORRAR un mensaje', 'DENY', () => deleteDoc(doc(dbDe(OWNER), ...MSGS.split('/'), 'm1')));
    await check('un tercero tampoco borra mensajes', 'DENY', () => deleteDoc(doc(dbDe(AJENO), ...MSGS.split('/'), 'm1')));
}

/* ------------------------------------------------------------------- runner */
(async () => {
    if (!process.env.FIRESTORE_EMULATOR_HOST) {
        console.error('Falta FIRESTORE_EMULATOR_HOST. Usa:  npm run reglas:test');
        process.exit(2);
    }
    testEnv = await initializeTestEnvironment({
        projectId: PROJECT,
        firestore: {
            rules: fs.readFileSync(RULES, 'utf8'),
            host: '127.0.0.1',
            port: Number(process.env.FIRESTORE_EMULATOR_HOST.split(':')[1]),
        },
    });
    try {
        await fase1_propuestas();
        await fase2_autoaceptacion();
        await fase3_chats();
        await fase4_pausa();
        await fase5_inmueble_finalizado();
        await fase6_denuncias();
        await fase7_citas();
        await fase8_soft_delete();
    } finally {
        await testEnv.cleanup();
    }
    console.log(fallos === 0 ? `\nTODO OK (${total} casos)\n` : `\n${fallos}/${total} FALLAS\n`);
    process.exit(fallos === 0 ? 0 : 1);
})().catch(e => { console.error('ERROR', e.stack || e.message); process.exit(1); });
