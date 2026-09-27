import re

with open('D:/alkilapp/app/src/main/java/com/alkilapp/RegistrarPropiedadActivity.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Find the function
pattern = r'(    /\*\*\n     \* Traduce las coordenadas elegidas en el mapa a una direccion \(geocodificacion inversa\)\n     \* y la rellena en el campo de direccion \(siempre actualiza al seleccionar en el mapa\)\.\n     \* Tambi.*?auto-completa departamento y distrito seg[uú]n c[oó]digo postal\.\n     \*/\n    private fun rellenarDireccionDesdeMapa\(lat: Double, lng: Double\) \{[\s\S]*?^\s{4}\}\n    \}'

new_func = '''/**
     * Traduce las coordenadas elegidas en el mapa a una direccion (geocodificacion inversa)
     * y la rellena en el campo de direccion (siempre actualiza al seleccionar en el mapa).
     * Tambi\u00e9n auto-completa departamento y distrito usando Geocoder con componentes administrativos.
     */
    private fun rellenarDireccionDesdeMapa(lat: Double, lng: Double) {
        CoroutineScope(Dispatchers.IO).launch {
            val direccion = try {
                Geocoder(this@RegistrarPropiedadActivity, Locale.getDefault())
                    .getFromLocation(lat, lng, 1)
                    ?.firstOrNull()
                    ?.getAddressLine(0)
            } catch (_: Exception) {
                null
            }
            
            if (!direccion.isNullOrBlank()) {
                runOnUiThread {
                    binding.etPropDireccion.setText(direccion)
                }
                
                val (departamento, distrito) = extraerUbicacionDesdeDireccion(direccion!!)
                
                withContext(Dispatchers.Main) {
                    if (departamento.isNotBlank()) {
                        seleccionarDepartamentoYActualizarDistritos(departamento)
                        binding.spPropDistrito.postDelayed({
                            seleccionarDistrito(distrito)
                        }, 200)
                    }
                }
            }
        }.start()
    }'''

with open('D:/alkilapp/app/src/main/java/com/alkilapp/RegistrarPropiedadActivity.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Find the function
pattern = re.compile(r'    /\*\*\n     \* Traduce las coordenadas elegidas en el mapa a una direccion \(geocodificacion inversa\)\n     \* y la rellena en el campo de direccion \(siempre actualiza al seleccionar en el mapa\)\.\n     \* Tambi[\u00e9n] auto-completa departamento y distrito seg[uú]n c[\u00f3o]digo postal\.\n     \*/\n    private fun rellenarDireccionDesdeMapa\(lat: Double, lng: Double\) \{[\s\S]*?^\s{4}\}\n    \}', re.MULTILINE | re.DOTALL)

match = re.search(r'(    /\*\*\n     \* Traduce las coordenadas elegidas en el mapa a una direccion \(geocodificacion inversa\)\n     \* y la rellena en el campo de direccion \(siempre actualiza al seleccionar en el mapa\)\.\n     \* Tambi[\u00e9n] auto-completa departamento y distrito seg[uú]n c[\u00f3o]digo postal\.\n     \*/\n    private fun rellenarDireccionDesdeMapa\(lat: Double, lng: Double\) \{[\s\S]*?^\s{4}\}\n    \})', content, re.MULTILINE | re.DOTALL)

if match:
    print("Found match")
    print(match.group(0)[:200])
else:
    print("Not found")

# Try a simpler approach - just find the function
idx = content.find('private fun rellenarDireccionDesdeMapa')
if idx >= 0:
    print(f"Found at index {idx}")
    # Find the end of the function
    # Count braces
    brace_count = 0
    start = idx
    for i in range(idx, len(content)):
        if content[i] == '{':
            brace_count += 1
        elif content[i] == '}':
            brace_count -= 1
            if brace_count == 0:
                end = i + 1
                print(f"Function ends at index {end}")
                print(f"Function length: {end - start}")
                print(content[start:end])
                break
    else:
        print("Function not found")