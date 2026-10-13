// Comprueba el HTML del aviso de inmueble ANTES de desplegar, mandando un
// correo de prueba a uno mismo.
//
// Se hace sobre una COPIA de index.js con una línea extra que expone el
// constructor del HTML. Es el mismo código que se despliega; solo se le añade
// un export para poder inspeccionarlo desde aquí. No se toca el original.
const fs = require('fs');
const path = require('path');
const os = require('os');

const original = path.join(__dirname, 'index.js');
// La copia va DENTRO de la carpeta de la funcion: si se pusiera en el temp,
// require('firebase-functions/v1') no encontraria node_modules.
const copia = path.join(__dirname, '_test-index.js');

let src = fs.readFileSync(original, 'utf8');
src += '\nmodule.exports.__construirHtmlInmueble = construirHtmlInmueble;\n';
src += 'module.exports.__precioLegible = precioLegible;\n';
fs.writeFileSync(copia, src, 'utf8');

const mod = require(copia);
const construir = mod.__construirHtmlInmueble;
const precioLegible = mod.__precioLegible;

let fallos = 0;
const ok = (c, m) => { console.log((c ? '  OK   ' : '  FALLA') + '  ' + m); if (!c) fallos++; };

console.log('=== precioLegible ===');
ok(precioLegible({ precio: 750, moneda: 'PEN' }) === 'S/ 750', 'PEN: ' + precioLegible({ precio: 750, moneda: 'PEN' }));
ok(precioLegible({ precio: 1200, moneda: 'USD' }) === 'US$ 1,200', 'USD: ' + precioLegible({ precio: 1200, moneda: 'USD' }));
ok(precioLegible({ precio: 0 }) === 'Sin precio', 'precio 0: ' + precioLegible({ precio: 0 }));
ok(precioLegible({}) === 'Sin precio', 'sin precio: ' + precioLegible({}));

console.log('\n=== HTML del aviso ===');
const html = construir({
    id: 'ABC123def456',
    titulo: 'Departamento 2 cuartos céntrico',
    tipo: 'departamento',
    operacion: 'alquiler',
    precio: 'S/ 750',
    ubicacion: 'Lima · Los Olivos',
    ambientes: '2 ambientes · 70 m²',
    superficie: 70,
    propietario: 'Jhoner Ramirez',
    publicadoEn: '2026-10-12T15:04:00.000Z',
});

ok(html.startsWith('<!DOCTYPE html>'), 'empieza con DOCTYPE');
ok(html.includes('</html>'), 'cierra el html');
ok(html.includes('Nuevo inmueble por aprobar'), 'titulo visible');
ok(html.includes('Departamento 2 cuartos céntrico'), 'titulo del inmueble');
ok(html.includes('Jhoner Ramirez'), 'nombre del propietario');
ok(html.includes('/publicaciones'), 'enlace al panel');
ok(html.includes('ABC123def456'), 'id del inmueble');
ok(html.includes('Los Olivos'), 'ubicacion');
ok(!html.includes('${'), 'no queda ninguna interpolacion sin resolver');
ok(!html.includes('undefined'), 'no aparece "undefined" en ningun sitio');
ok(!html.includes('[object Object]'), 'no aparece [object Object]');

// El titulo lo escribe el usuario: tiene que ir escapado, no inyectar markup.
const htmlMalo = construir({
    id: 'x', titulo: '<img src=x onerror="alert(1)">', tipo: 'd', operacion: 'a',
    precio: 'S/ 1', ubicacion: 'u', ambientes: 'a', superficie: 0,
    propietario: '<b>no</b>', publicadoEn: new Date().toISOString(),
});
ok(!htmlMalo.includes('<img src=x'), 'titulo malicioso escapado (no hay <img>)');
ok(htmlMalo.includes('&lt;img'), 'el texto malicioso se ve literalmente');
ok(!htmlMalo.includes('<b>no</b>'), 'nombre del propietario tambien escapado');

// Anchos de tabla y estilos: que no falte ningun cierre de <tr>/<td>.
const tr = (htmlMalo.match(/<tr/g) || []).length;
const trC = (htmlMalo.match(/<\/tr>/g) || []).length;
const td = (htmlMalo.match(/<td/g) || []).length;
const tdC = (htmlMalo.match(/<\/td>/g) || []).length;
ok(tr === trC, `cada <tr> cierra (${tr}/${trC})`);
ok(td === tdC, `cada <td> cierra (${td}/${tdC})`);

console.log('\n=== muestra del HTML (300 primeros caracteres del cuerpo) ===');
console.log(html.slice(html.indexOf('<h1'), html.indexOf('<h1') + 220));

fs.unlinkSync(copia);
console.log('\n' + (fallos ? 'FALLOS: ' + fallos : 'TODO OK'));
process.exit(fallos ? 1 : 0);