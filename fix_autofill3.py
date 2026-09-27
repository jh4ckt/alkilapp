with open('D:/alkilapp/app/src/main/java/com/alkilapp/RegistrarPropiedadActivity.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Find the function
idx = content.find('private fun rellenarDireccionDesdeMapa')
if idx >= 0:
    print(f'Found at index {idx}')
    
    # Find the end of the function by counting braces
    brace_count = 0
    in_string = False
    escape_next = False
    start = idx
    for i in range(idx, len(content)):
        ch = content[i]
        if not in_string:
            if ch == '{':
                brace_count += 1
            elif ch == '}':
                brace_count -= 1
                if brace_count == 0:
                    end = i + 1
                    old_func = content[idx:end]
                    print(f'Function ends at index {end}')
                    print(f'Function length: {len(old_func)} chars')
                    break
            elif ch == '"' and not escape_next:
                in_string = True
            elif ch == '\\' and not escape_next:
                escape_next = True
            else:
                escape_next = False
        else:
            if ch == '"' and not escape_next:
                in_string = True
            elif ch == '\\' and not escape_next:
                escape_next = True
            else:
                escape_next = False
    else:
        print('Function not found')
        exit(1)
    
    old_func = content[idx:end]
    print(f'Function length: {len(old_func)} chars')
    
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

if old_func in content:
    content = content.replace(old_func, new_func)
    with open('D:/alkilapp/app/src/main/java/com/alkilapp/RegistrarPropiedadActivity.kt', 'w', encoding='utf-8') as f:
        f.write(content)
    print('Replaced successfully')
else:
    print('OLD FUNCTION NOT FOUND - trying flexible match')
    idx = content.find('private fun rellenarDireccionDesdeMapa')
    if idx >= 0:
        print(f'Found at index {idx}')
        # Find the end
        brace_count = 0
        in_string = False
        escape_next = False
        start = idx
        for i in range(idx, len(content)):
            ch = content[i]
            if not in_string:
                if ch == '{':
                    brace_count += 1
                elif ch == '}':
                    brace_count -= 1
                    if brace_count == 0:
                        end = i + 1
                        old_func = content[start:end]
                        print(f'Found function from {idx} to {end}, length {len(content[idx:end])}')
                        print(content[idx:idx+200])
                        break
                elif ch == '"' and not escape_next:
                    in_string = True
                elif ch == '\\' and not escape_next:
                    escape_next = True
                else:
                    escape_next = False
            else:
                if ch == '"' and not escape_next:
                    in_string = True
                elif ch == '\\' and not escape_next:
                    escape_next = True
                else:
                    escape_next = False
            if brace_count == 0 and i > idx:
                end = i + 1
                old_func = content[start:end]
                print(f'Found function from {idx} to {end}, length {len(content[idx:end])}')
                print(content[idx:idx+200])
                break
        else:
            print('Function not found')