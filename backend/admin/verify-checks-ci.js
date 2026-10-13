// Comprueba que los verificadores del CI DETECTAN un problema de verdad.
// Un check que nunca falla no protege de nada, asi que aqui se rompen a
// proposito y se exige que devuelvan codigo de salida 1. Luego se restaura.
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const COMP = path.join(__dirname, 'public', 'js', 'components', 'Stats.js');
const orig = fs.readFileSync(COMP, 'utf8');

function correr(args, cwd) {
    try {
        const out = execFileSync('node', args, { cwd, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] });
        return { codigo: 0, salida: out };
    } catch (e) {
        return { codigo: e.status === undefined ? -1 : e.status, salida: (e.stdout || '') + (e.stderr || '') };
    }
}

let fallos = 0;
const ok = (c, m) => { console.log((c ? '  OK   ' : '  FALLA') + '  ' + m); if (!c) fallos++; };

try {
    // --- 1) style= mal formado debe hacer fallar verify-estilos.js ---
    const i = orig.indexOf('style="');
    const j = orig.indexOf('"/>', i);
    if (i < 0 || j < 0) throw new Error('no encuentro un style=" completo en Stats.js');
    fs.writeFileSync(COMP, orig.slice(0, i) + 'style="color: var(--card-bg' + orig.slice(j), 'utf8');
    const r1 = correr(['verify-estilos.js'], __dirname);
    ok(r1.codigo === 1, 'verify-estilos.js FALLA con un style= sin cerrar (salida ' + r1.codigo + ')');
    ok(/ATRIBUTOS CON PROBLEMAS/.test(r1.salida), '  y explica cual es el problema');

    // --- 2) sintaxis rota debe hacer fallar verify-sintaxis.js ---
    // Un "const roto = {" NO vale: abre un bloque y el resto del archivo lo
    // puede cerrar sin problema, asi que sigue siendo JavaScript valido. Se usa
    // un error inequivoco.
    fs.writeFileSync(COMP, 'export const roto = ;\n' + orig, 'utf8');
    const r2 = correr(['--experimental-vm-modules', 'verify-sintaxis.js'], __dirname);
    ok(r2.codigo === 1, 'verify-sintaxis.js FALLA con sintaxis rota (salida ' + r2.codigo + ')');
    ok(/FALLA/.test(r2.salida), '  y senala el archivo con problema');
} finally {
    // Siempre se restaura, pase lo que pase.
    fs.writeFileSync(COMP, orig, 'utf8');
}

// --- 3) con el archivo restaurado, todo vuelve a pasar ---
const r3 = correr(['verify-estilos.js'], __dirname);
ok(r3.codigo === 0, 'tras restaurar, verify-estilos.js vuelve a pasar');
const r4 = correr(['--experimental-vm-modules', 'verify-sintaxis.js'], __dirname);
ok(r4.codigo === 0, 'tras restaurar, verify-sintaxis.js vuelve a pasar');
ok(fs.readFileSync(COMP, 'utf8') === orig, 'el archivo quedo EXACTAMENTE como estaba');

console.log('\n' + (fallos ? 'FALLOS: ' + fallos : 'TODO OK: los checks del CI detectan fallos de verdad'));
process.exit(fallos ? 1 : 0);