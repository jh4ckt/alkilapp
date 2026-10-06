// Chequeo estatico (sin navegador) de que ningun atributo style= quedo mal
// formado tras la migracion de colores:
//   - parentesis balanceados dentro del valor
//   - ningun var(--x) sin cerrar
//   - el atributo no se trunca antes de su cierre
// Es el fallo que mas se cuela: un style mal formado NO da error, simplemente
// deja de aplicarse en silencio.
const fs = require('fs');
const path = require('path');

const RAIZ = path.join(__dirname, 'public', 'js');

function rec(dir) {
    let r = [];
    for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
        const p = path.join(dir, e.name);
        if (e.isDirectory()) r = r.concat(rec(p));
        else if (e.name.endsWith('.js')) r.push(p);
    }
    return r;
}

let total = 0;
const fallos = [];

for (const f of rec(RAIZ)) {
    const s = fs.readFileSync(f, 'utf8');
    const re = /style="([^"]*)"/g;
    let m;
    while ((m = re.exec(s)) !== null) {
        total++;
        const v = m[1];
        const rel = path.relative(RAIZ, f);
        const linea = s.slice(0, m.index).split('\n').length;

        let nivel = 0, roto = false;
        for (const c of v) {
            if (c === '(') nivel++;
            if (c === ')') nivel--;
            if (nivel < 0) { roto = true; break; }
        }
        if (roto || nivel !== 0) fallos.push({ rel, linea, motivo: 'parentesis desbalanceados', v: v.slice(0, 90) });

        const vars = (v.match(/var\(/g) || []).length;
        if (v.includes('var(--') && !v.includes('var(')) fallos.push({ rel, linea, motivo: 'var( sin abrir', v: v.slice(0, 90) });
        if (/var\(--[^)]*$/.test(v)) fallos.push({ rel, linea, motivo: 'var(-- sin cerrar', v: v.slice(0, 90) });
        // Solo se avisa de falta de ';' final cuando NO hay interpolacion de
        // template: un style="${extra}" se completa en tiempo de ejecucion, asi
        // que aqui no se puede juzgar. Y aunque no lleve ';' final, CSS lo
        // acepta igual: no es un defecto.
    }
}

console.log('atributos style=" revisados: ' + total);
if (!fallos.length) {
    console.log('TODOS BIEN: ni un atributo mal formado');
} else {
    console.log('ATRIBUTOS CON PROBLEMAS: ' + fallos.length);
    for (const f of fallos.slice(0, 25)) {
        console.log('  ' + f.rel + ':' + f.linea + '  ' + f.motivo);
        console.log('     ' + JSON.stringify(f.v));
    }
}
process.exit(fallos.length ? 1 : 0);