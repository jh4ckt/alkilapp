## Ronda 24 (v1.38 / vc20)

### ✅ Registro con celular obligatorio
- Nuevo campo **Celular** en la pantalla de crear cuenta (obligatorio, >=9 dígitos).
- Se guarda en `usuarios/{uid}.telefono` al crear la cuenta.

### ✅ Mi Perfil: nombre y correo no editables (datos únicos)
- El nombre pasa a solo lectura (label + valor, igual que el correo).
- Nota: "Tu nombre y correo no se pueden modificar."
- guardarPerfil() ya no sobrescribe nombre; solo guarda teléfono y tipo de cuenta.

### ✅ Verificación de identidad: 2 fotos (frente + adverso)
- Pantalla con dos pickers: **Frente del documento** y **Adverso (dorso)**.
- Ambos obligatorios para enviar a revisión.
- Se guardan en `verificaciones/{uid}.imagen` (frente) e `imagenReverso` (adverso).
- Reglas Firestore create exigen ambos campos (<=900KB cada uno).

### ✅ Panel Admin: filtros + enlaces en Resumen
- **Verificaciones**: búsqueda + filtro por estado (pendiente/aprobado/rechazado).
- **Publicaciones**: búsqueda + filtro por estado + filtro destacado (sí/no).
- **Denuncias**: búsqueda + filtro por estado (pendiente/resuelto).
- **Usuarios**: búsqueda + filtros por tipo, estado de verificación, nivel de confianza.
- **Resumen**: las tarjetas de contadores son enlaces clicables a cada pestaña.

### Técnico
- Firestore rules v9 desplegadas (ruleset e8b5deb4).
- Sesiones admin stateless (cookie firmada con HMAC, TTL 24h, sin store en memoria).
- Version bump: versionCode 20, versionName 1.38.
- Build: gradlew assembleDebug, APK instalado en Xiaomi 14 (serial 6phyeanrfyhmozv8).