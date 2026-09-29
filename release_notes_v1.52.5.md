## AlkilApp Android v1.52.5 (vc34)

### Correcciones de bugs reportados

- **BUG-01**: Etiqueta de operación (Venta/Alquiler) + periodicidad `/mes` en precio del detalle
- **BUG-02**: Textos adaptados para Venta: "Ya lo vendí", "¿Ya vendiste?", banner "ya fue vendido"
- **BUG-03**: Logout cierra sesión en Google también (`GoogleSignIn.signOut()`); selector de cuentas al reingresar (parcial: `setPrompt` no disponible en versión actual)
- **BUG-04**: GPS tiene prioridad sobre zona de perfil para ordenar por cercanía
- **BUG-08**: Precio visible en cabecera del chat con periodicidad
- **BUG-09**: Debounce **5 segundos** en `enviarMensaje()` para evitar duplicados
- **BUG-10**: Hint búsqueda mapa acortado: "Buscar inmueble, distrito, zona..."
- **BUG-11**: `direccionCompleta()` usa `.distinct()` para evitar duplicados
- **BUG-12**: Ortografía Verificación corregida: `Subi→Sube`, `Numero→Número`, `revisión`, `adverso→reverso`, tildes en `sesión`, `revisión`, `verificación`
- **BUG-13**: Strings `Pausar publicación`, `Reactivar publicación`, `Eliminar publicación` con tildes correctas

### Build
- **versionCode**: 34 (>33)
- **versionName**: 1.52.5
- **minSdk**: 24 / **targetSdk**: 36
- **Proguard/R8**: habilitado
- **Keystore**: regenerado (SHA1 `F9:53...`), reset en Play Console aprobado para 29/sep

### APK
- `app-release.apk` (6.5 MB) firmado y listo para instalar