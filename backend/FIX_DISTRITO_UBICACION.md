# Fix: el distrito no se guardaba / no se mostraba al editar una propiedad

**Sintoma reportado:** al crear una publicación o al entrar a "Editar", el campo Distrito
mostraba **"Sin distrito"** aunque el usuario ya lo había elegido. El departamento, en cambio,
sí se restauraba correctamente.

**Archivo afectado:** `app/src/main/java/com/alkilapp/RegistrarPropiedadActivity.kt`

---

## 1. Diagnóstico: los datos SÍ se guardaban

Antes de tocar código se verificó directamente en Firestore (`alkilappdb` → colección `propiedades`):

```json
{"id":"NCJ28Pq9KSizwX3v8C5k","ciudad":"Amazonas","barrio":"Chachapoyas","estado":"publicado"}
{"id":"WkBbKHIsTVha4Vv549II","ciudad":"Amazonas","barrio":"Chachapoyas","estado":"publicado"}
{"id":"eGmsXQosS3bhgJAdzvhT","ciudad":"Amazonas","barrio":"Chachapoyas","estado":"disponible"}
```

`barrio` guardaba correctamente `"Chachapoyas"`. **El guardado nunca estuvo roto** — el problema
era exclusivamente al *restaurar* el valor en el spinner de la ventana de edición.

## 2. Causa raíz real: el adapter se reconstruye DESPUÉS de aplicar la selección

El spinner de Distrito se construía en **dos lugares distintos**:

1. `cargarDatosParaEditar()` — armaba el adapter y aplicaba el distrito guardado vía
   `spPropDistrito.post { setSelection(indice) }`
2. El `onItemSelected` del spinner de **Departamento** — volvía a armar el adapter desde cero
   cada vez que se disparaba el evento de selección del departamento

El punto clave es que **`Spinner.setSelection()` NO invoca `onItemSelected` de forma síncrona**:
el callback se despacha en un *layout pass* posterior. Por eso la secuencia real era:

```
1. cargarDatosParaEditar  -> setSelection(departamento)      (pide layout diferido)
2. cargarDatosParaEditar  -> arma adapter de Distrito
3. cargarDatosParaEditar  -> post { setSelection("Chachapoyas") }   -> queda "Chachapoyas"
4. LAYOUT PASS            -> onItemSelected del Departamento
                            -> RE-ARMA el adapter de Distrito   <-- selection vuelve a 0
5.                                       "Sin distrito"   <-- BUG
```

Reemplazar el adapter de un Spinner reinicia su selección al índice 0 (`"Sin distrito"`),
descartando el valor que se había aplicado en el paso 3.

**Por qué el Departamento sí funcionaba:** su adapter nunca se reconstruye; solo se le cambia
la posición. Por eso mostraba "Amazonas" mientras el Distrito quedaba en "Sin distrito".

**Por qué fallaron los intentos anteriores** (`v1.61.0` / `v1.61.1`): el problema nunca fue de
normalización de strings. `normalizarDepto`, `normalizarTexto` y el `.trim()` eran correctos y el
índice 2 (`"Chachapoyas"`) se encontraba bien — luego se borraba. Se venía corrigiendo el
comparador cuando el defecto era de **orden de ejecución**.

## 3. Solución: una única fuente de verdad para el adapter

Se eliminó la construcción duplicada. Ahora existe un solo método autoritativo:

```kotlin
private fun aplicarDistritosParaDepartamento(departamento: String) {
    val ciudades = obtenerCiudades(departamento)
    val opciones = if (departamento == "Lima") {
        listOf(getString(R.string.prop_distrito_sin)) + ciudades
    } else {
        listOf(getString(R.string.prop_distrito_sin), "Otro") + ciudades
    }
    binding.spPropDistrito.adapter = crearAdapterSpinner(opciones)

    val pendiente = distritoPendiente
    if (!pendiente.isNullOrBlank() && pendiente != getString(R.string.prop_distrito_sin)) {
        val objetivo = normalizarTexto(pendiente)
        val indice = opciones.indexOfFirst { normalizarTexto(it) == objetivo }
        binding.spPropDistrito.post {
            binding.spPropDistrito.setSelection(indice.coerceAtLeast(0))
        }
        distritoPendiente = null
    }
}
```

- El `onItemSelected` del Departamento es el **único** que arma el adapter:
  `aplicarDistritosParaDepartamento(departamentos[position])`
- `cargarDatosParaEditar()` ya **no** arma el adapter ni aplica la selección. Solo deja el valor
  guardado en `distritoPendiente` y pide el departamento:

```kotlin
distritoPendiente = distritoSel
spinnerDepartamento.setSelection(depArr.indexOfFirst { ... }.coerceAtLeast(0))
```

Como el adapter se crea y se selecciona ladistrict en el **mismo** callback, el resultado ya no
depende del orden entre el `post{}` y el layout pass.

### Cambio adicional de consistencia

`continuarGuardado()` (publicaciones nuevas) solo escribía `barrio`, mientras que el modo edición
escribía `barrio` **y** `distrito`. Se igualaron ambos caminos de guardado.

## 4. Verificación en dispositivo real (Xiaomi `6phyeanrfyhmozv8`)

Recorrido: MainActivity → menú → "Ver mis publicaciones" → Editar → Paso 3 de 5 (Ubicación),
leído con `uiautomator dump`.

| Propiedad | Departamento | Distrito (antes) | Distrito (después) |
|---|---|---|---|
| Amazonas (`NCJ28Pq9…`) | Amazonas | `Sin distrito` ❌ | **`Chachapoyas`** ✅ |
| Lima (`GScB7bGK…`) | Lima | — | **`Los Olivos`** ✅ |

Se cubrieron ambas ramas del `if`: Lima (sin `"Otro"` en la lista) y no-Lima (con `"Other"→"Otro"`),
confirmando que no hay regresión en ninguna.

## 5. Nota sobre versiones anteriores

`v1.61.0` y `v1.61.1` se publicaron con un AAB potencialmente desactualizado (no se reejecutó
`:app:bundleRelease` tras el último commit) y con la lógica de doble construcción del adapter,
por lo que **no contienen este fix**. Este fix entra en la versión nueva.