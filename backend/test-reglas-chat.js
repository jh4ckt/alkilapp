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
    doc, setDoc, updateDoc, addDoc, collection, getDoc, getDocs, serverTimestamp,
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
    } finally {
        await testEnv.cleanup();
    }
    console.log(fallos === 0 ? `\nTODO OK (${total} casos)\n` : `\n${fallos}/${total} FALLAS\n`);
    process.exit(fallos === 0 ? 0 : 1);
})().catch(e => { console.error('ERROR', e.stack || e.message); process.exit(1); });
