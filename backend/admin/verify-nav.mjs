import fs from 'fs';

const dir = 'D:/alkilapp/backend/admin/public';
const idx = fs.readFileSync(dir + '/index.html', 'utf8');

console.log('=== index.html (producción local) ===');
for (const m of idx.matchAll(/<a[^>]*class="nav-item"[^>]*>[\s\S]{0,400}?data-page="([^"]+)"?/g)) {
  // simplificado: reviso por separado
}
// Bloque nav: extraigo cada <a ...>
const links = [...idx.matchAll(/<a[^>]*?(?:class="(brand|nav-item)"|data-page=")([^"]*)"?[^>]*?>/g)];
for (const m of links) {
  const t = m[0];
  const page = (t.match(/data-page="([^"]+)"/) || [])[1] || '(logo)';
  const href = (t.match(/href="([^"]+)"/) || [])[1] || 'SIN HREF';
  console.log(String(page).padEnd(16), '->', href);
}

console.log('\n=== app.js listeners ===');
const app = fs.readFileSync(dir + '/js/app.js', 'utf8');
console.log('preventDefault:', /preventDefault\(\)/.test(app));
console.log('pushState:', /pushState\(/.test(app));
console.log('handleNav:', /handleNav/.test(app));
console.log('logoutBtn:', /logoutBtn/.test(app));
console.log('event delegation (document.addEventListener click):', /document\.addEventListener\('click'/.test(app));
