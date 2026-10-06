// Verifica en PRODUCCION que /api/resumen traiga los contadores de "hoy".
// El login usa las credenciales reales de ADMIN_PASSWORD: si no esta en el
// entorno, se salta la prueba de API y solo comprueba que el panel responde.
const base = 'https://alkilapp-admin-288429280561.us-central1.run.app';

(async () => {
    const pagina = await fetch(base + '/login');
    console.log('[login page]', pagina.status);
    const html = await pagina.text();
    console.log('  form de login presente:', /name="password"/.test(html));

    if (!process.env.ADMIN_PASSWORD) {
        console.log('\n[INFO] ADMIN_PASSWORD no esta en el entorno: no puedo autenticar.');
        console.log('        Se omite la prueba de /api/resumen (queda para el operador).');
        return;
    }

    const login = await fetch(base + '/login', {
        method: 'POST',
        headers: { 'content-type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({
            user: process.env.ADMIN_USER || 'admin',
            password: process.env.ADMIN_PASSWORD,
        }),
        redirect: 'manual',
    });
    const cookie = (login.headers.get('set-cookie') || '').split(';')[0];
    console.log('\n[login]', login.status, '| cookie ok:', cookie.startsWith('alkil_admin='));
    if (!cookie) { console.log('  sin cookie, abortando'); return; }

    const r = await fetch(base + '/api/resumen', { headers: { cookie } });
    const resumen = await r.json();
    console.log('[resumen]', r.status);
    console.log(JSON.stringify(resumen, null, 2));

    const s = await (await fetch(base + '/api/stats?days=30', { headers: { cookie } })).json();
    console.log('\n[stats] series presentes:',
        'propiedadesTrend:', Array.isArray(s.propiedadesTrend?.values),
        '| usuariosTrend:', Array.isArray(s.usuariosTrend?.values));
    console.log('  propiedades:', JSON.stringify(s.propiedadesTrend?.values));
    console.log('  usuarios   :', JSON.stringify(s.usuariosTrend?.values));
})().catch((e) => { console.error('FALLO:', e.message); process.exit(1); });