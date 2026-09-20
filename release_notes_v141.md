## AlkilApp v1.41 (vc23)

### ✅ Deep Links (alkilapp://propiedad/{id})
- **Manifest**: `PropiedadDetalleActivity` exportada con intent-filter `alkilapp://propiedad/*`
- **Share button**: genera `alkilapp://propiedad/{propId}` en lugar de URL web
- **Apertura directa**: al tocar el link (WhatsApp, email, etc.) abre la app directamente en el detalle
- **Carga desde Firestore**: si viene por deep link, `PropiedadDetalleActivity` carga los datos por `propId`

### ✅ Fix: Detalle desde "Mis Publicaciones"
- Removido `EXTRA_FOTOS` (base64) del intent → evitaba `TransactionTooLargeException`
- Ahora usa `EXTRA_FOTOS_URL` + carga por ID desde Firestore (igual que MainActivity)

### ✅ Fix chat permission denied
- Revertido `.sorted()` en array `participants` (PropiedadDetalleActivity + PerfilPropietarioActivity)
- Mantiene orden original al crear chat → evita `PERMISSION_DENIED` en regla Firestore

### Técnico
- Version bump: `versionCode 23`, `versionName 1.41`
- Build: `gradlew assembleDebug` OK
- Commit: `9535b17`