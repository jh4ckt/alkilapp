/**
 * Verifica el doc creado por el registro E2E realizado en el dispositivo:
 * - accounts:signInWithPassword con el correo/contrasena del QA.
 * - GET Firestore REST de documentos/usuarios/{uid}
 * Imprime el doc completo. NO borra nada (el usuario QA se limpia luego con limpiar-pruebas).
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

const EMAIL = process.argv[2];
const PASSWORD = process.argv[3];
if (!EMAIL || !PASSWORD) { console.error("Uso: node verif-registro-e2e.cjs <email> <password>"); process.exit(1); }

async function signInWithPassword() {
  const res = await fetch(
    `https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${apiKey}`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Android-Package": "com.alkilapp",
        "X-Android-Cert": CERT,
      },
      body: JSON.stringify({ email: EMAIL, password: PASSWORD, returnSecureToken: true }),
    }
  );
  const data = await res.json();
  if (!res.ok) { console.error("signInWithPassword fallo:", res.status, JSON.stringify(data)); process.exit(1); }
  return data;
}

async function getDoc(uid) {
  const res = await fetch(
    `https://firestore.googleapis.com/v1/projects/${PROJECT}/databases/alkilappdb/documents/usuarios/${uid}`,
    { headers: { Authorization: `Bearer ${(await signInWithPassword()).idToken}` } }
  );
  if (!res.ok) {
    console.error("GET usuarios/{uid} fallo:", res.status, await res.text());
    process.exit(1);
  }
  return res.json();
}

(async () => {
  const auth = await signInWithPassword();
  console.log("USUARIO_AUTH:", JSON.stringify({ uid: auth.localId, email: auth.email, emailVerified: auth.emailVerified, createdAt: auth.createdAt }, null, 2));
  const doc = await getDoc(auth.localId);
  console.log("DOC_USUARIOS_EXISTE:", !!doc.fields);
  if (doc.fields) {
    const fl = {};
    for (const [k, v] of Object.entries(doc.fields)) fl[k] = v.stringValue ?? v.booleanValue ?? v.timestampValue ?? v.integerValue ?? v;
    console.log("DOC_FIELDS:", JSON.stringify(fl, null, 2));
  }
})().catch((e) => { console.error(e); process.exit(1); });