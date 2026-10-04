# AlkilApp v1.60.0 (versionCode 42)

## 🏠 Chat List - Agrupación por Inmueble + "Iniciado por ti"

- **Agrupación por inmueble**: Los chats ahora se agrupan por inmueble (`listingId`), mostrando una cabecera con el título del inmueble y sus chats debajo
- **Badge "Iniciado por ti"**: Cuando `creatorId == miUid`, el chat muestra un badge azul "Iniciado por ti" para diferenciar los chats que tú iniciaste de los que te iniciaron
- Campo `creatorId` y `creadoEn` agregados al modelo `ChatAlkil`

## ❤️ Favoritos - Badge Corregido + Limpieza en Logout

- **Badge preciso**: Ahora lee directo de `Favoritos.locales(this)` (SharedPreferences) en lugar de un cache en memoria que podía estar desactualizado
- **Limpieza en logout**: Al cerrar sesión (`MiPerfilActivity`), se llama `Favoritos.limpiarLocales(this)` que borra el cache local
- **Fix**: El badge ya no muestra "1" cuando no hay favoritos

## 📧 Verificación de Email - Separada de DNI/Identidad

- **Separación clara**: La verificación de email (`emailVerified`) ahora es independiente de la verificación de DNI/identidad (`identityVerified`, `status: 'aprobado'`)
- **Admin panel**: Endpoint `POST /api/usuarios/:uid/verificar-email` solo setea `emailVerified=true` + `trustLevel=basic` + `verificationBadge=true` — **NO toca** `identityVerified` ni `verification.status`
- Reglas Firestore actualizadas: `verificationPermitida()` solo valida si el cliente modifica el campo `verification`

## 🐛 Bug Fixes

- **Badge favoritos**: Ya no muestra "1" cuando no hay favoritos (leía de cache stale)
- **AndroidManifest**: `ChatListActivity` con `android:exported="false"` (era `true`)
- **Reglas Firestore**: `verificationPermitida()` ahora solo valida si el cliente MODIFICA el campo `verification`

## 📦 Build

- **versionCode**: 42
- **versionName**: 1.60.0
- AAB: `app/build/outputs/bundle/release/app-release.aab` (9.5 MB)