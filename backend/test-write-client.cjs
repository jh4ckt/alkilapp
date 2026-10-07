/**
 * Prueba EMPIRICA de que la app no puede CREAR su doc en /usuarios:
 * 1. Crea un usuario de Auth real (Identity Toolkit REST con la API key de la app).
 * 2. Replica el set(merge) de guardarUsuarioEnBase como cliente Firestore REST
 *    (mismo doc usuario, mismas reglas desplegadas) para REGISTRO y para GOOGLE.
 * 3. Observa el veredicto real: 200 = crea, 403 = regla deniega.
 * Al final borra el usuario de Auth (accounts:delete con su propio idToken).
 */
const fs = require("fs");
const path = require("path");

const PROJECT = "gen-lang-client-0040505884";
const CERT = "b4ed9dc44203e58db68eb745fc7beda902cc879f";
const PARENT = path.resolve(__dirname, "..");
const gs = JSON.parse(fs.readFileSync(path.join(PARENT, "app", "google-services.json"), "utf8").replace(/^\uFEFF/, ""));
const apiKey = gs.client
  .find((c) => c.client_info.android_client_info.package_name === "com.alkilapp")
  .api_key[0].current_key;
if (!apiKey) { console.error("No encontre la API key en google-services.json"); process.exit(1); }

const usuarios = [
  {
    caso: "registro email/password (payload de AuthActivity)",
    email: `qa-reglas-${Date.now()}@alkilapp.test`,
    password: "Secreto123!",
    write: (uid) => ({
      nombre: { stringValue: "Prueba Registro" },
      email: { stringValue: `qa-reglas-${Date.now()}@alkilapp.test` },
      uidAuth: { stringValue: uid },
      activo: { booleanValue: true },
      telefono: { stringValue: "999888777" },
      zonaDepartamento: { stringValue: "Lima" },
      tipoUsuario: { stringValue: "dueno" },
      fechaRegistro: { timestampValue: new Date().toISOString() },
    }),
  },
  {
    caso: "login Google (payload de MainActivity, sin zonaDepartamento)",
    email: `qa-google-${Date.now()}@prueba.com`,
    password: "Secreto123!",
    write: (uid) => ({
      nombre: { stringValue: "Prueba Google" },
      email: { stringValue: `qa-google-${Date.now()}@prueba.com` },
      uidAuth: { stringValue: uid },
      activo: { booleanValue: true },
      telefono: { stringValue: "" },
      tipoUsuario: { stringValue: "dueno" },
      fechaRegistro: { timestampValue: new Date().toISOString() },
    }),
  },
];

async function signUp(email, password) {
  const res = await fetch(
    `https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${apiKey}`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Android-Package": "com.alkilapp",
        "X-Android-Cert": CERT,
      },
      body: JSON.stringify({ email, password, returnSecureToken: true }),
    }
  );
  const j = await res.json();
  if (!res.ok) throw new Error(`signUp ${res.status}: ${JSON.stringify(j)}`);
  return j;
}

async function firestoreWrite(uid, fields) {
  const res = await fetch(
    `https://firestore.googleapis.com/v1/projects/${PROJECT}/databases/alkilappdb/documents/usuarios?documentId=${uid}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${global.tok}` },
      body: JSON.stringify({ fields }),
    }
  );
const body = await res.text();
  return { status: res.status, body: body.slice(0, 500) };
}

async function firestoreWriteCol(col, documentId, fields) {
  const res = await fetch(
    `https://firestore.googleapis.com/v1/projects/${PROJECT}/databases/alkilappdb/documents/${col}?documentId=${documentId}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${global.tok}` },
      body: JSON.stringify({ fields }),
    }
  );
  const body = await res.text();
  return { status: res.status, body: body.slice(0, 300) };
}

(async () => {
  for (const u of usuarios) {
    console.log(`\n=== ${u.caso} ===`);
    let idt;
    let authRes;
    try {
      authRes = await signUp(u.email, u.password);
      idt = authRes.idToken;
      global.tok = idt;
      console.log(`  creado auth uid=${authRes.localId}`);
    } catch (e) {
      console.log(`  ERROR creando auth usuario: ${e.message}`);
      continue;
    }
    try {
      const r = await firestoreWrite(authRes.localId, u.write(authRes.localId));
      console.log(`  firestore POST -> HTTP ${r.status}`);
      console.log(`  body: ${r.body}`);
    } catch (e) {
      console.log(`  ERROR write: ${e.message}`);
    }
// limpieza: borrar el usuario de Auth con su propio idToken
    try {
      const res = await fetch(
        `https://identitytoolkit.googleapis.com/v1/accounts:delete?key=${apiKey}`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json", "X-Android-Package": "com.alkilapp", "X-Android-Cert": CERT },
          body: JSON.stringify({ idToken: idt }),
        }
      );
      console.log(`  limpieza accounts:delete -> HTTP ${res.status}`);
    } catch (e) {
      console.log(`  ERROR limpieza: ${e.message}`);
    }
  }

  // SANITY: favoritos permite el create de cualquier autenticado (auth=? vs regla).
  {
    const probe = { email: `qa-sanity-${Date.now()}@alkilapp.test`, password: "Secreto123!" };
    const ares = await signUp(probe.email, probe.password);
    global.tok = ares.idToken;
    const docId = `${ares.localId}__prop-sanity`;
    const r = await firestoreWriteCol(
      "favoritos", docId,
      { uid: { stringValue: ares.localId }, propiedadId: { stringValue: "prop-sanity" } }
    );
    console.log(`\n=== sanity favoritos (comprueba que el bearer autentica) ===`);
    console.log(`  firestore POST favoritos -> HTTP ${r.status} body=${r.body}`);
    if (r.status === 200) {
      const del = await fetch(
        `https://firestore.googleapis.com/v1/projects/${PROJECT}/databases/alkilappdb/documents/favoritos/${docId}`,
        { method: "DELETE", headers: { Authorization: `Bearer ${global.tok}` } }
      );
      console.log(`  limpieza favorito -> HTTP ${del.status}`);
    }
    const delAuth = await fetch(
      `https://identitytoolkit.googleapis.com/v1/accounts:delete?key=${apiKey}`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json", "X-Android-Package": "com.alkilapp", "X-Android-Cert": CERT },
        body: JSON.stringify({ idToken: ares.idToken }),
      }
    );
    console.log(`  limpieza auth sanity -> HTTP ${delAuth.status}`);
  }
})().catch((e) => { console.error(e); process.exit(1); });
