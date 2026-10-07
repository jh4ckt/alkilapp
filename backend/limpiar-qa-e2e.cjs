/**
 * Limpia el usuario QA del registro E2E: borra el Auth user y su doc /usuarios.
 * Uso: node limpiar-qa-e2e.cjs <email> <password>
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

async function main() {
  const sign = await fetch(
    `https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${apiKey}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-Android-Package": "com.alkilapp", "X-Android-Cert": CERT },
      body: JSON.stringify({ email: EMAIL, password: PASSWORD, returnSecureToken: true }),
    }
  );
  const auth = await sign.json();
  if (!sign.ok) { console.error("signin fallo:", sign.status, JSON.stringify(auth)); process.exit(1); }
  console.log("Sesion del QA:", auth.localId);

  const delAuth = await fetch(
    `https://identitytoolkit.googleapis.com/v1/accounts:delete?key=${apiKey}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-Android-Package": "com.alkilapp", "X-Android-Cert": CERT },
      body: JSON.stringify({ idToken: auth.idToken }),
    }
  );
  console.log("accounts:delete ->", delAuth.status, await delAuth.text());

  const delDoc = await fetch(
    `https://firestore.googleapis.com/v1/projects/${PROJECT}/databases/alkilappdb/documents/usuarios/${auth.localId}`,
    { method: "DELETE", headers: { Authorization: `Bearer ${auth.idToken}` } }
  );
  console.log("DELETE usuarios/{uid} ->", delDoc.status);
}

main().catch((e) => { console.error(e); process.exit(1); });