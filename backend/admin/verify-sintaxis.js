// Comprueba que los .js del admin parsean como modulo ES y que no tienen
// null bytes (un null dentro de un identificador rompe el bundle entero).
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const RAIZ = path.join(__dirname, 'public', 'js');
let fallos = 0;

function recorrer(dir) {
    for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
        const p = path.join(dir, e.name);
        if (e.isDirectory()) { recorrer(p); continue; }
        if (!e.name.endsWith('.js')) continue;
        revisar(p);
    }
}

function revisar(p) {
    const b = fs.readFileSync(p);
    let nulls = 0;
    for (let i = 0; i < b.length; i++) if (b[i] === 0) nulls++;
    if (nulls) { console.log('  FALLA  ' + p + '  null bytes=' + nulls); fallos++; return; }

    const src = b.toString('utf8');
    try {
        new vm.SourceTextModule(src, { identifier: p });
        console.log('  OK     ' + path.relative(RAIZ, p));
    } catch (e) {
        console.log('  FALLA  ' + path.relative(RAIZ, p) + '  ' + e.message);
        fallos++;
    }
}

recorrer(RAIZ);
console.log(fallos ? '\nFALLOS: ' + fallos : '\nSintaxis OK en todos los .js');
process.exit(fallos ? 1 : 0);