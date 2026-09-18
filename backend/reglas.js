/*
 * Utilidad para leer y desplegar las reglas de Firestore de "alkilappdb".
 *
 *   node reglas.js show                 -> imprime las reglas activas
 *   node reglas.js deploy <archivo>     -> publica un nuevo conjunto de reglas
 *
 * Nota: la API de releases no acepta PATCH con service account; el deploy
 * hace DELETE + POST con el nombre completo en el body.
 */
const fs = require('fs');
const path = require('path');
const { GoogleAuth } = require('google-auth-library');

const PROJECT = 'gen-lang-client-0040505884';
const DATABASE = 'alkilappdb';
const SA = process.env.GOOGLE_APPLICATION_CREDENTIALS ||
    path.join(__dirname, 'credentials', 'alkilapp-seed-sa.json');
const BASE = `https://firebaserules.googleapis.com/v1/projects/${PROJECT}`;
const RELEASE_ID = `cloud.firestore/${DATABASE}`;

async function token() {
    const auth = new GoogleAuth({
        keyFilename: SA,
        scopes: [
            'https://www.googleapis.com/auth/cloud-platform',
            'https://www.googleapis.com/auth/firebase',
        ],
    });
    const client = await auth.getClient();
    return (await client.getAccessToken()).token;
}

async function api(method, url, body, tok) {
    const res = await fetch(url, {
        method,
        headers: {
            Authorization: `Bearer ${tok}`,
            'Content-Type': 'application/json',
        },
        body: body ? JSON.stringify(body) : undefined,
    });
    const text = await res.text();
    if (!res.ok) throw new Error(`${method} ${url} -> ${res.status} ${text}`);
    return text ? JSON.parse(text) : null;
}

async function show() {
    const tok = await token();
    const rel = await api('GET', `${BASE}/releases/${RELEASE_ID}`, null, tok);
    const rs = await api('GET', `https://firebaserules.googleapis.com/v1/${rel.rulesetName}`, null, tok);
    console.log(rs.source.files.map((f) => f.content).join('\n'));
}

async function deploy(file) {
    if (!file) throw new Error('falta el archivo de reglas');
    const tok = await token();
    const content = fs.readFileSync(file, 'utf8');
    const created = await api('POST', `${BASE}/rulesets`, {
        source: { files: [{ name: 'firestore.rules', content }] },
    }, tok);
    try {
        await api('DELETE', `${BASE}/releases/${RELEASE_ID}`, null, tok);
    } catch (e) {
        console.warn('aviso al borrar el release previo:', e.message);
    }
    await api('POST', `${BASE}/releases`, {
        name: `projects/${PROJECT}/releases/${RELEASE_ID}`,
        rulesetName: created.name,
    }, tok);
    console.log('OK ->', created.name);
}

const cmd = process.argv[2];
const run = cmd === 'show' ? show : cmd === 'deploy' ? () => deploy(process.argv[3]) : null;
if (!run) {
    console.log('uso: node reglas.js show | node reglas.js deploy <archivo.rules>');
    process.exit(1);
}
run().catch((e) => {
    console.error('ERROR:', e.message);
    process.exit(1);
});
