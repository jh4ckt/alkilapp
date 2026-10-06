// Prueba end-to-end contra el server HTTP real (no contra las funciones).
// Verifica que /api/resumen trae las distribuciones y que los filtros de
// /api/publicaciones devuelven exactamente los mismos conteos que las KPIs.
// Apunta al servidor local por defecto; con PROD_URL=1 contra el de Cloud Run.
const BASE = process.env.PROD_URL
    || ('http://localhost:' + (process.env.PORT || 8099));
const PASS = process.env.ADMIN_PASSWORD;

// El panel no usa header: se hace login contra /api/login y se reenvia la cookie
// de sesion (alkil_admin) en cada peticion.
let COOKIE = '';

async function login() {
    // El login real es POST /login y espera application/x-www-form-urlencoded
    // (no JSON), y devuelve HTML, no JSON.
    const r = await fetch(BASE + '/login', {
        method: 'POST',
        headers: { 'content-type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ user: process.env.ADMIN_USER || 'admin', password: PASS }),
        redirect: 'manual',
    });
    const set = r.headers.get('set-cookie') || '';
    COOKIE = set.split(';')[0];
    if (!COOKIE.startsWith('alkil_admin=')) {
        throw new Error('login fallo (HTTP ' + r.status + '): ' + (await r.text()).slice(0, 200));
    }
}

let fallos = 0;
const ok = (c, m) => { console.log((c ? '  OK   ' : '  FALLA') + '  ' + m); if (!c) fallos++; };

async function get(path) {
    const r = await fetch(BASE + path, { headers: { cookie: COOKIE } });
    if (!r.ok) throw new Error(path + ' -> HTTP ' + r.status + ' ' + (await r.text()).slice(0, 200));
    return r.json();
}

(async () => {
    await login();
    console.log('login OK, cookie de sesion obtenida\n');

    console.log('=== /api/resumen ===');
    const resumen = await get('/api/resumen');
    ok(resumen.propiedades.total === 15, 'propiedades.total = ' + resumen.propiedades.total);
    const dist = resumen.distribuciones;
    ok(!!dist, 'resumen.distribuciones presente');
    if (dist) {
        for (const k of ['departamento', 'ciudad', 'tipo', 'operacion', 'estado', 'moneda', 'tramoPrecio']) {
            ok(Array.isArray(dist[k]) && dist[k].length > 0, 'distribuciones.' + k + ' -> ' + (dist[k] || []).map(x => x.clave + '=' + x.total).join(' '));
        }
        const suma = (k) => dist[k].reduce((a, x) => a + x.total, 0);
        ok(suma('departamento') === 15 && suma('tipo') === 15 && suma('tramoPrecio') === 15,
            'todas las dimensiones suman 15');
        ok(dist.departamento.every((x) => x.etiqueta && x.etiqueta !== ''), 'todas las claves traen etiqueta legible');
    }

    console.log('\n=== /api/publicaciones: opciones ===');
    const todas = await get('/api/publicaciones?limit=1');
    ok(todas.total === 15, 'sin filtros -> total 15 (recibidos ' + todas.total + ')');
    ok(!!todas.opciones, 'la respuesta trae `opciones` para los <select>');
    if (todas.opciones) {
        ok(Array.isArray(todas.opciones.departamento) && todas.opciones.departamento.length > 0,
            'opciones.departamento -> ' + todas.opciones.departamento.map(x => x.clave + '(' + x.total + ')').join(' '));
        // El front lee `valor`, no `clave`. Sin este alias los <select> salian
        // con value="undefined".
        ok(todas.opciones.departamento.every((x) => x.valor !== undefined && x.valor === x.clave),
            'cada opcion trae `valor` (alias de clave) para el <select>');
        ok(todas.opciones.departamento.every((x) => typeof x.etiqueta === 'string' && x.etiqueta),
            'cada opcion trae etiqueta legible');
        ok(todas.opciones.tramoPrecio.every((x) => x.etiqueta && !/^[a-z_]+$/.test(x.etiqueta)),
            'los tramos de precio ya salen traducidos: ' + todas.opciones.tramoPrecio.map(x => x.etiqueta).join(' / '));
        ok(todas.opciones.estado.every((x) => x.etiqueta === 'Disponible' || x.etiqueta === 'Finalizado'),
            'los estados salen etiquetados en español: ' + todas.opciones.estado.map(x => x.etiqueta).join(' / '));
        // Al filtrar, las opciones NO deben encogerse: si lo hacen, al elegir un
        // valor las demas opciones desaparecen y no hay vuelta atras.
        const filtrada = await get('/api/publicaciones?departamento=Lima&limit=1');
        ok(filtrada.total === 10, 'departamento=Lima -> ' + filtrada.total + ' docs');
        ok(filtrada.opciones.departamento.length === todas.opciones.departamento.length,
            'las opciones siguen completas al filtrar (' + filtrada.opciones.departamento.length + ')');
        ok(filtrada.opciones.tipo.length === todas.opciones.tipo.length,
            'opciones de OTRA dimension tambien se mantienen (' + filtrada.opciones.tipo.length + ')');
    }

    console.log('\n=== filtros cruzados ===');
    const casos = [
        ['departamento=Amazonas', 4],
        ['departamento=Hu%C3%A1nuco', 1],
        ['tipo=departamento', 8],
        ['tipo=habitacion', 4],
        ['operacion=alquiler', 14],
        ['operacion=venta', 1],
        ['moneda=USD', 1],
        ['tramoPrecio=economico', 8],
        ['tramoPrecio=medio', 4],
        ['tramoPrecio=premium', 3],
        ['estado=disponible', 10],
        ['estado=finalizado', 5],
    ];
    for (const [q, esperado] of casos) {
        const r = await get('/api/publicaciones?' + q + '&limit=50');
        ok(r.total === esperado, q + ' -> ' + r.total + ' (esperado ' + esperado + ')');
    }

    console.log('\n=== combinados ===');
    const combo1 = await get('/api/publicaciones?departamento=Lima&tramoPrecio=medio&limit=50');
    ok(combo1.total === 3, 'Lima + medio -> ' + combo1.total);
    ok(combo1.data.every((p) => p.departamento === 'Lima'), 'todos los resultados son de Lima');
    const combo2 = await get('/api/publicaciones?tipo=departamento&operacion=venta&limit=50');
    ok(combo2.data.every((p) => p.tipo === 'departamento' && p.operacion === 'venta'),
        'departamento + venta: ' + combo2.total + ' docs, todos coherentes');
    const combo3 = await get('/api/publicaciones?departamento=Lima&estado=disponible&limit=50');
    ok(combo3.data.length === combo3.total, 'Lima + disponible: ' + combo3.total + ' docs');
    ok(combo3.data.every((p) => p.estadoCanonico === 'disponible'),
        'todos los resultados de Lima+disponible son canonicamente disponibles');
    ok(combo3.data.every((p) => p.departamento === 'Lima'), 'y todos son de Lima');

    // El estado crudo debe seguir viendose: el <select> de la fila lo necesita
    // para poder guardar un cambio sin inventarse el valor.
    ok(combo3.data.every((p) => typeof p.estado === 'string' && p.estado !== ''),
        'las filas siguen trayendo el estado crudo');

    console.log('\n=== sin resultados ===');
    const vacio = await get('/api/publicaciones?departamento=Lima&tramoPrecio=economico&operacion=venta&limit=10');
    ok(vacio.total === 0, 'combo imposible -> 0 (sin error)');

    console.log('\n=== paginacion sigue funcionando ===');
    const p1 = await get('/api/publicaciones?limit=5&page=1');
    const p2 = await get('/api/publicaciones?limit=5&page=2');
    ok(p1.data.length === 5 && p2.data.length === 5, 'dos paginas de 5');
    ok(p1.total === 15 && p2.total === 15, 'el total no cambia al paginar');
    ok(p1.data[0].id !== p2.data[0].id, 'las paginas traen filas distintas');

    console.log('\n=== el listado trae departamento (para la fila) ===');
    ok(p1.data.every((p) => p.departamento), 'las filas incluyen `departamento`');

    console.log('\n' + (fallos ? 'FALLOS: ' + fallos : 'TODO OK'));
    process.exit(fallos ? 1 : 0);
})().catch((e) => { console.error('ERROR:', e.message); process.exit(1); });