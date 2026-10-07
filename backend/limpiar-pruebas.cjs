/** Limpia docs de prueba creados por test-write-client.cjs (usa la SA, sin reglas). */
const path = require("path");
const { GoogleAuth } = require("google-auth-library");
const PROJECT = "gen-lang-client-0040505884";

const docs = [
  "usuarios/geK065V4QChpJXOoRZXhOwJzJEm2",
  "usuarios/XkquT9PT6PhjaX0FG1OboJnovD63",
  "favoritos/SoYBbeZd2DeJuLmQjI8BW0JeA0b2__prop-sanity",
  "favoritos/5j7WLyZcoCWz2EzPbo6cDqlSNNq2__prop-sanity",
];

(async () => {
  const auth = new GoogleAuth({ keyFile: path.join(__dirname, "credentials", "alkilapp-seed-sa.json"), scopes: ["https://www.googleapis.com/auth/cloud-platform", "https://www.googleapis.com/auth/datastore", "https://www.googleapis.com/auth/firebase"] });
  const client = await auth.getClient();
  const token = await client.getAccessToken();
  for (const d of docs) {
    const url = `https://firestore.googleapis.com/v1/projects/${PROJECT}/databases/alkilappdb/documents/${d}`;
    const r = await fetch(url, { method: "DELETE", headers: { Authorization: `Bearer ${token.token}` } });
    console.log(`DELETE ${d} -> ${r.status}`);
  }
})().catch((e) => { console.error(e); process.exit(1); });