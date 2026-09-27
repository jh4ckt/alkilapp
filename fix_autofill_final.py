with open('D:/alkilapp/app/src/main/java/com/alkilapp/RegistrarPropiedadActivity.kt', 'r', encoding='utf-8') as f:
    content = f.read()

start = content.find('private fun rellenarDireccionDesdeMapa(lat: Double, lng: Double) {')
if start >= 0:
    end = content.find('\n    /** Si hay permiso', content.find('private fun rellenarDireccionDesdeMapa'))
    if start >= 0 and end > start:
        old_func = content[start:end]
        print(f'Found function, length: {len(content[start:end])}')
        
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
        
        if content[start:content.find('\n    /** Si hay permiso', start)] == content[start:content.find('\n    /** Si hay permiso', start)]:
            old_func = content[start:content.find('\n    /** Si hay permiso', start)]
            new_content = content[:start] + new_func + content[content.find('\n    /** Si hay permiso', start):]
            
            with open('D:/alkilapp/app/src/main/java/com/alkilapp/RegistrarPropiedadActivity.kt', 'w', encoding='utf-8') as f:
                f.write(content)
            print('Replaced successfully')
        else:
            print('Could not find exact boundaries')
else:
    print('Function not found')