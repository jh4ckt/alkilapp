// E2E: login -> cookie -> cargar index y js -> apuntar a /api/resumen
const fs = require('fs');
const path = require('path');

const BASE = 'https://alkilapp-admin-288429280561.us-central1.run.app';
const ADMIN_DIR = 'D:\\alkilapp\\backend\\admin';

(async () => {
  // 1) Login
  const login = await fetch(BASE + '/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: 'user=admin&password=test123',
    redirect: 'manual',
  });
  const setCookie = login.headers.get('set-cookie') || '';
  console.log('[login] status:', login.status, '| redirect:', login.headers.get('location'));
  console.log('[login] cookie:', setCookie.slice(0, 60) + '...');
  const cookie = setCookie.split(';')[0];

  // 2) GET / (index.html) con cookie
  const idx = await fetch(BASE + '/', { headers: { cookie } });
  const html = await idx.text();
  console.log('[get /] status:', idx.status, '| len:', html.length);

  // refs a JS/CSS en el HTML servido
  const refs = [...html.matchAll(/(?:src|href)="(\/(?:js|css)[^"]*)"/g)].map(m => m[1]);
  console.log('[refs]:', refs.join(', '));

  // 3) API resumen (con la cookie)
  const res = await fetch(BASE + '/api/resumen', {
    headers: { cookie, accept: 'application/json' },
  });
  const body = await res.text();
  console.log('[api/resumen] status:', res.status);
  console.log('[api/resumen] body:', body.slice(0, 300));

  // 4) Verificar que los archivos JS existen en disco (no 404)
  for (const ref of refs.filter(r => r.includes('.js'))) {
    const local = path.join(ADMIN_DIR, 'public', ref.replace(/^\//, '').split('?')[0]);
    console.log('[local]', ref, '->', fs.existsSync(local) ? 'EXISTE' : 'NO EXISTE');
  }
})();
