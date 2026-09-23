import { execSync } from 'child_process';
import fs from 'fs';

const V = 99oplat
// producción: https://alkilapp-admin-288429280561.us-central1.run.app
const DOMAIN = 'https://alkilapp-admin-288429280561.us-central1.run.app';
const tmp = 'D:/alkilapp/scratch-parse';
fs.mkdirSync(tmp, { recursive: true });

const urls = [
  '/js/app.js',
  '/js/app.old.js',
  '/js/components/Dashboard.js',
  '/js/components/Verificaciones.js',
  '/js/components/Publicaciones.js',
  '/js/components/Reportes.js',
  '/js/components/Usuarios.js',
  '/js/components/Stats.js',
  '/js/services/api.js',
  '/js/utils/helpers.js',
];

for (const u of urls) {
  try {
    const r = await fetch(DOMAIN + u + '?v=99');
    const code = await r.text();
    if (r.status !== 200) { console.log('HTTP ' + r.status + '  ' + u); continue; }
    const name = u.replace(/\//g, '_').slice(1) + '.mjs';
    const f = tmp + '/' + name;
    fs.writeFileSync(f, code);
    try {
      execSync('node --check "' + f + '"', { stdio: 'pipe' });
      console.log('SINTAXIS OK  ' + u);
    } catch (e) {
      console.log('!!!! ERROR SINTAXIS  ' + u);
      console.log(e.stderr ? e.stderr.toString().split('\n').slice(0, 8).join('\n') : e.message.slice(0, 500));
    }
  } catch (e) {
    console.log('FETCH FALLO  ' + u + ' -> ' + e.message);
  }
}
console.log('\n(nota: 4-componentes bajan de producción solo si existen; dashboard esperado OK)');
