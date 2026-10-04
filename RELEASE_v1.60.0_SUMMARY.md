# Release AlkilApp v1.60.0 (vc42) - Resumen

**Fecha**: 2026-10-03  
**Tag**: `v1.60.0`  
**Commit**: `19b5f38`  
**AAB**: `app/build/outputs/bundle/release/app-release.aab` (9.5 MB)  
**Release**: https://github.com/jh4ckt/alkilapp/releases/tag/v1.60.0

---

## 📋 Cambios Principales

### 🏠 Chat List - Agrupación por Inmueble + "Iniciado por ti"
- **Agrupación por inmueble**: Los chats ahora se agrupan por `listingId`, con cabecera del inmueble
- **Badge "Iniciado por ti"**: Cuando `creatorId == miUid`, badge azul diferenciado
- Modelo `ChatAlkil`: + `creatorId: String?`, `creadoEn: Long?`
- `PropiedadDetalleActivity`: al crear chat, `creatorId = miUid`, `creadoEn = now`
- `ChatListActivity`: agrupa por `listingId`, pasa `Map<String, List<ChatAlkil>>` al adapter
- `ChatListAdapter`: `TYPE_HEADER` + `TYPE_ITEM`, badge "Iniciado por ti" (`chat.creatorId == miUid`)

### ❤️ Favoritos - Badge Corregido + Limpieza en Logout
- **Badge preciso**: `actualizarBadgeFavoritos()` lee `Favoritos.locales(this).size` directo de SharedPreferences
- **Limpieza en logout**: `Favoritos.limpiarLocales(ctx)` borra cache local al cerrar sesión
- `MiPerfilActivity`: llama `Favoritos.limpiarLocales(this)` en `cerrarSesion`
- **Fix**: Badge ya no muestra "1" sin favoritos (cache stale)

### 📧 Verificación Email - Separada de DNI/Identidad
- **Separación clara**: `emailVerified` (email) vs `identityVerified` + `status: 'aprobado'` (DNI)
- **Admin panel**: `POST /api/usuarios/:uid/verificar-email` → solo `emailVerified=true` + `trustLevel=basic` + `verificationBadge=true`
- **Reglas Firestore**: `verificationPermitida()` solo valida si cliente MODIFICA `verification` (`affectedKeys.hasAll(['verification'])`)

### 🐛 Bug Fixes
- Badge favoritos: ya no muestra "1" sin favoritos (leía cache stale)
- `AndroidManifest`: `ChatListActivity` → `android:exported="false"`
- `verificationPermitida()`: solo valida si `affectedKeys.hasAll(['verification'])`

## 📦 Build
| Campo | Valor |
|-------|-------|
| **versionCode** | 42 |
| **versionName** | 1.60.0 |
| **AAB** | `app/build/outputs/bundle/release/app-release.aab` (9.5 MB) |
| **Release** | https://github.com/jh4ckt/alkilapp/releases/tag/v1.60.0 |
| **Commit** | `19b5f38` |

## 🔄 Próximos Pasos
- [ ] Subir AAB a Play Console (Internal Testing)
- [ ] Probar en dispositivo real el flujo completo de chat + favoritos + email verification
- [ ] Verificar panel admin: verificación manual de email + DNI separados