/**
 * Verifica la MISMA logica que EstadoCuenta.kt contra los datos reales de
 * alkilappdb/usuarios, ya que el Xiaomi no estaba conectado para probar en vivo.
 *
 * Replica consulta + normalizacion: doc.getString("estado") -> trim -> lowercase
 * == "desactivado". Un error de lectura equivale a "permitido".
 */
const path = require("path");
const { Firestore } = require("@google-cloud/firestore");

const db = new Firestore({
  projectId: "gen-lang-client-0040505884",
  databaseId: "alkilappdb",
  keyFilename: path.join(__dirname, "credentials", "alkilapp-seed-sa.json"),
});

const esDesactivado = (estado) =>
  estado === null || estado === undefined
    ? false
    : String(estado).trim().toLowerCase() === "desactivado";

(async () => {
  const snap = await db.collection("usuarios").get();

  const filas = snap.docs.map((d) => ({
    uid: d.id,
    email: d.data().email ?? "(sin email)",
    estadoCrudo: d.data().estado ?? null,
    bloqueado: esDesactivado(d.data().estado),
  }));

  const bloqueados = filas.filter((f) => f.bloqueado);
  const permitidos = filas.filter((f) => !f.bloqueado);

  console.log(`Total usuarios: ${filas.length}`);
  console.log(`\nBLOQUEADOS (${bloqueados.length}):`);
  bloqueados.forEach((f) =>
    console.log(`  ${f.email}  estado=${JSON.stringify(f.estadoCrudo)}  -> BLOQUEADO`)
  );

  console.log(`\nPERMITIDOS (${permitidos.length}) - resumen:`);
  const porEstado = {};
  permitidos.forEach((f) => {
    const k = f.estadoCrudo === null ? "(sin campo)" : JSON.stringify(f.estadoCrudo);
    porEstado[k] = (porEstado[k] || 0) + 1;
  });
  Object.entries(porEstado)
    .sort((a, b) => b[1] - a[1])
    .forEach(([k, n]) => console.log(`  estado=${k}: ${n}`));

  // Casos limite que la app debe manejar bien.
  console.log("\nNormalizacion (trim + lowercase):");
  [
    ["desactivado", true],
    ["DESACTIVADO", true],
    ["  desactivado  ", true],
    ["Desactivado", true],
    ["activo", false],
    ["suspendido", false],
    ["desactivadoTemporal", false],
    ["", false],
    [null, false],
    [undefined, false],
  ].forEach(([valor, esperado]) => {
    const real = esDesactivado(valor);
    console.log(`  ${real === esperado ? "OK  " : "FALLA"} ${JSON.stringify(valor)} -> ${real} (esperado ${esperado})`);
  });

  const fallos = filas.length === 0;
  if (fallos) console.log("\nSIN DATOS: revisa el proyecto/base");
})();