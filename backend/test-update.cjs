/** Valida que el update de /usuarios sigue correcto tras el fix:
 *  - update telefono (legitimo) -> 200
 *  - update estado=desactivado (auto-desact) -> 403 (regresion del bloqueo)
 *  Crea su propio usuario+doc y se limpia solo.
 */
const fs = require("fs");
const path = require("path");
const PROJECT = "gen-lang-client-0040505884";
const CERT = "b4ed9dc44203e58db68eb745fc7beda902cc879f";
const gs = JSON.parse(fs.readFileSync(path.join(__dirname, "..", "app", "google-services.json"), "utf8").replace(/^\uFEFF/, ""));
const apiKey = gs.client.find((c) => c.client_info.android_client_info.package_name === "com.alkilapp").api_key[0].current_key;

async function itRaw(url, body, tok) {
  const res = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Android-Package": "com.alkilapp", "X-Android-Cert": CERT, ...(tok ? { Authorization: `Bearer ${tok}` } : {}) },
    body: JSON.stringify(body),
  });
  return { status: res.status, body: await res.text() };
}

(async () => {
  const email = `qa-update-${Date.now()}@alkilapp.test`;
  const su = await itRaw(`https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${apiKey}`, { email, password: "Secreto123!", returnSecureToken: true });
  const sj = JSON.parse(su.body);
  if (su.status !== 200 || !sj.idToken) { console.log("signUp fallo:", su.status, su.body.slice(0, 300)); return; }
  const j = { idToken: sj.idToken, localId: sj.localId };
  console.log(`usuario ${j.localId} creado`);

const uid = j.localId;
  const colUrl = `https://firestore.googleapis.com/v1/projects/${PROJECT}/databases/alkilappdb/documents/usuarios`;
  const docUrl = `${colUrl}/${uid}`;
  const hdr = { Authorization: `Bearer ${j.idToken}` };
  const raw = { "Content-Type": "application/json", ...hdr };

  // crear doc (POST a la coleccion con documentId)
  let r = await fetch(`${colUrl}?documentId=${uid}`, { method: "POST", headers: raw, body: JSON.stringify({ fields: { nombre: { stringValue: "QA Update" }, email: { stringValue: email }, activo: { booleanValue: true } } }) });
  const createBody = await r.text();
  console.log(`create -> ${r.status} ${createBody.slice(0, 160)}`);
  if (r.status !== 200) return;

  // UPDATE telefono (legitimo, update sobre doc existente)
r = await fetch(`${docUrl}?updateMask.fieldPaths=telefono`, { method: "PATCH", headers: raw, body: JSON.stringify({ fields: { telefono: { stringValue: "999999999" } } }) });
  const upTelefono = await r.text();
  console.log(`update telefono -> ${r.status} ${upTelefono.slice(0, 250)}`);

  // UPDATE estado=desactivado (debe DENEGARSE)
  r = await fetch(`${docUrl}?updateMask.fieldPaths=estado`, { method: "PATCH", headers: raw, body: JSON.stringify({ fields: { estado: { stringValue: "desactivado" } } }) });
  const upEstado = await r.text();
  console.log(`update estado=desactivado -> ${r.status} ${upEstado.slice(0, 250)} ${r.status === 403 ? "DENY (ok)" : (r.status === 200 ? "!!! PERMITIDO" : "revisar")}`);

  // limpieza: borrar doc y usuario de auth
  r = await fetch(docUrl, { method: "DELETE", headers: hdr });
  console.log(`delete doc -> ${r.status}`);
  r = await itRaw(`https://identitytoolkit.googleapis.com/v1/accounts:delete?key=${apiKey}`, { idToken: j.idToken });
  console.log(`delete auth -> ${r.status}`);
})().catch((e) => { console.error(e); process.exit(1); });
