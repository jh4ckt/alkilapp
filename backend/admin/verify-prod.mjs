// Verifica el HTML real servido por Cloud Run (producción)
const base = 'https://alkilapp-admin-288429280561.us-central1.run.app';

(async () => {
  const login = await fetch(base + '/login', {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: 'user=admin&password=test123',
    redirect: 'manual',
  });
  const cookie = (login.headers.get('set-cookie') || '').split(';')[0];
  console.log('[login]', login.status, '| cookie:', cookie.slice(0, 30) + '...');

  const res = await fetch(base + '/', { headers: { cookie } });
  const html = await res.text();
  console.log('[index] status:', res.status, 'len:', html.length);

  console.log('\n=== nav items (spanish, as served) ===');
  for (const m of html.matchAll(/<a class="nav-item"[^>]*>/g)) {
    const page = m[0].match(/data-page="([^"]+)"/)?.[1] || '(logo)';
    const href = m[0].match(/href="([^"]+)"/)?.[1] || 'SIN HREF';
    console.log(String(page).padEnd(16), href);
  }

  console.log('\n=== cache-bust refs ===');
  console.log('css:', html.match(/styles\.css[^"]*/)?.[0] || '?');
  console.log('app:', html.match(/app\.js[^"]*/)?.[0] || '?');
})();
