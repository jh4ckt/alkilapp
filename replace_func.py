with open('D:/alkilapp/app/src/main/java/com/alkilapp/RegistrarPropiedadActivity.kt', 'r', encoding='utf-8') as f:
    content = f.read()

old_func = '''    /**
     * Traduce las coordenadas elegidas en el mapa a una direccion (geocodificacion inversa)
     * y la rellena en el campo de direccion (siempre actualiza al seleccionar en el mapa).
     * Tambi\u00e9n auto-completa departamento y distrito seg\u00fan c\u00f3digo postal.
     */
    private fun rellenarDireccionDesdeMapa(lat: Double, lng: Double) {
        Thread {
            val direccion = try {
                Geocoder(this, Locale.getDefault())
                    .getFromLocation(lat, lng, 1)
                    ?.firstOrNull()
                    ?.getAddressLine(0)
            } catch (_: Exception) {
                null
            }
            Log.d("AlkilAppBilling", "rellenarDireccionDesdeMapa: lat=$lat, lng=$lng, direccion=$direccion")
            if (!direccion.isNullOrBlank()) {
                runOnUiThread {
                    binding.etPropDireccion.setText(direccion)
                }
                Thread {
                    try {
                        val geocoder = Geocoder(this@RegistrarPropiedadActivity, Locale.getDefault())
                        val addresses = geocoder.getFromLocationName(direccion!!, 1)
                        if (!addresses.isNullOrEmpty()) {
                            Log.d("AlkilAppBilling", "geocoder results: ${addresses.size}")
                            val address = addresses[0]
                            val postalCode = address.postalCode
                            Log.d("AlkilAppBilling", "postalCode: $postalCode")
                            if (!postalCode.isNullOrBlank()) {
                                val (departamento, distrito) = departamentoYDistritoDesdePostalCode(postalCode!!)
                                Log.d("AlkilAppBilling", "departamento=$departamento, distrito=$distrito")
                                if (departamento.isNotBlank()) {
                                    runOnUiThread {
                                        val depArr = resources.getStringArray(R.array.departamentos_peru)
                                        val depIndex = depArr.indexOfFirst { it == departamento }
                                        if (depIndex >= 0) {
                                            binding.spPropDepartamento.setSelection(depIndex)
                                        }
                                        binding.spPropDistrito.postDelayed({
                                            val distArr = binding.spPropDistrito.adapter as? ArrayAdapter<*>
                                            val distList = distArr?.let { 
                                                (0 until it.count).map { index -> it.getItem(index).toString() } 
                                            } ?: emptyList()
                                            val distIndex = distList.indexOfFirst { it == distrito }
                                            if (distIndex >= 0) {
                                                binding.spPropDistrito.setSelection(distIndex)
                                            }
                                        }, 100)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("AlkilAppBilling", "Error en geocodificaci\u00f3n inversa: ${e.message}")
                    }
                }.start()
            }
        }.start()
    }

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

old_func = '''    /**
     * Traduce las coordenadas elegidas en el mapa a una direccion (geocodificacion inversa)
     * y la rellena en el campo de direccion (siempre actualiza al seleccionar en el mapa).
     * Tambi\u00e9n auto-completa departamento y distrito seg\u00fan c\u00f3digo postal.
     */
    private fun rellenarDireccionDesdeMapa(lat: Double, lng: Double) {
        Thread {
            val direccion = try {
                Geocoder(this, Locale.getDefault())
                    .getFromLocation(lat, lng, 1)
                    ?.firstOrNull()
                    ?.getAddressLine(0)
            } catch (_: Exception) {
                null
            }
            Log.d("AlkilAppBilling", "rellenarDireccionDesdeMapa: lat=$lat, lng=$lng, direccion=$direccion")
            if (!direccion.isNullOrBlank()) {
                runOnUiThread {
                    binding.etPropDireccion.setText(direccion)
                }
                Thread {
                    try {
                        val geocoder = Geocoder(this@RegistrarPropiedadActivity, Locale.getDefault())
                        val addresses = geocoder.getFromLocationName(direccion!!, 1)
                        if (!addresses.isNullOrEmpty()) {
                            Log.d("AlkilAppBilling", "geocoder results: ${addresses.size}")
                            val address = addresses[0]
                            val postalCode = address.postalCode
                            Log.d("AlkilAppBilling", "postalCode: $postalCode")
                            if (!postalCode.isNullOrBlank()) {
                                val (departamento, distrito) = departamentoYDistritoDesdePostalCode(postalCode!!)
                                Log.d("AlkilAppBilling", "departamento=$departamento, distrito=$distrito")
                                if (departamento.isNotBlank()) {
                                    runOnUiThread {
                                        val depArr = resources.getStringArray(R.array.departamentos_peru)
                                        val depIndex = depArr.indexOfFirst { it == departamento }
                                        if (depIndex >= 0) {
                                            binding.spPropDepartamento.setSelection(depIndex)
                                        }
                                        binding.spPropDistrito.postDelayed({
                                            val distArr = binding.spPropDistrito.adapter as? ArrayAdapter<*>
                                            val distList = distArr?.let { 
                                                (0 until it.count).map { index -> it.getItem(index).toString() } 
                                            } ?: emptyList()
                                            val distIndex = distList.indexOfFirst { it == distrito }
                                            if (distIndex >= 0) {
                                                binding.spPropDistrito.setSelection(distIndex)
                                            }
                                        }, 100)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("AlkilAppBilling", "Error en geocodificaci\u00f3n inversa: ${e.message}")
                    }
                }.start()
            }
        }.start()
    }

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
    }

if old_func in content:
    content = content.replace(old_func, new_func)
    with open('D:/alkilapp/app/src/main/java/com/alkilapp/RegistrarPropiedadActivity.kt', 'w', encoding='utf-8') as f:
        f.write(content)
    print('Replaced successfully')
else:
    print('OLD FUNCTION NOT FOUND')
    idx = content.find('private fun rellenarDireccionDesdeMapa')
    if idx >= 0:
        print(f'Found at index {idx}')
        print(content[idx:idx+500])
    else:
        print('Function not found')
"