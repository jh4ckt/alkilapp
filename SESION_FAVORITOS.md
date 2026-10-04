# Resumen sesión: Favoritos en menú hamburguesa (completo)

## Estado final
- **Commit**: `73aee6d` (pushed a master)
- **Versión**: `41` / `1.59.0` (sin bump aún)
- **Build**: `BUILD SUCCESSFUL` (debug APK instalado y verificado en Xiaomi `6phyeanrfyhmozv8`)
- **Último deploy Firestore rules**: `29db8482-f257-4451-b156-82392307b383`

## Qué se implementó (favoritos completo)
| Archivo | Cambio |
|---|---|
| `panel_menu.xml` | Fila `btnNavFavoritos` debajo de `btnNavChat` con badge `tvNavFavoritosBadge` |
| `strings.xml` | `nav_favoritos`, `favoritos_titulo`, `favoritos_cargando`, `favoritos_vacio`, `favoritos_actualizar` |
| `activity_favoritos.xml` | Nueva pantalla con header (título + contador + refresh), `rvFavoritos`, estados carga/vacío |
| `FavoritosActivity.kt` | Carga local instantánea, sync manual (botón refresh), corazon quita favorito, detalle con EXTRA_* completos |
| `Favoritos.kt` | **Tombstones** (`deleted_<id>`) para que borrados no revivan; sync merge `local ∪ (remote - tombstones)` |
| `PropiedadAdapter.kt` | Corazon = `MaterialButton` (clickable sin propagar); root sin listener; icono lleno/vacío |
| `item_propiedad.xml` | Corazon 44dp MaterialButton; MaterialCardView sin clickable/focusable |
| `MainActivity.kt` | `actualizarBadgeFavoritos()` sincroniza contador del menu |
| `AndroidManifest.xml` | `<activity android:name=".FavoritosActivity" android:exported="false" />` |
| `ic_refresh.xml` | Nuevo icono vector para botón refresh |

## Verificación completa (live en Xiaomi)
✅ Fila "Favoritos" en menu hamburguesa debajo de "Mis chats" con badge contador  
✅ Tap fila abre `FavoritosActivity` (requiere sesión)  
✅ Lista muestra propiedades guardadas con tarjetas idénticas al listado principal  
✅ Corazon en tarjeta quita favorito **instantáneamente** (UI + Firestore + cache local)  
✅ Contador header y badge menu se actualizan al instante  
✅ **Persistencia tras reinicio**: borrados NO reviven (tombstones `deleted_<id>` en prefs)  
✅ Sync manual (botón refresh) trae favoritos de otros dispositivos sin revivir borrados  
✅ Detalle desde favoritos pasa **todos** los `EXTRA_*` (título, precio, dirección, hab/m², comodidades, propietario, verificación, etc.)  
✅ "Ver detalles" en tarjeta abre ficha; corazon no propaga click a detalle  
✅ Empty state con mensaje y icono cuando no hay favoritos  
✅ Rotación restaurada (`accelerometer_rotation=1`, `user_rotation=0`, `stayon=false`)

## Diferidos (del v1.59.0, no tocar ahora)
- Placeholder mapa
- Test DNI inválido/pendiente en vivo
- Test "Ya lo vendí" (no hay publicación segura)
- Subida manual AAB a Play Console Internal Testing

## Comandos útiles
```bash
# Build debug
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug --offline

# Install
$adb install -r app/build/outputs/apk/debug/app-debug.apk

# Rotación
$adb shell settings put system accelerometer_rotation 1
$adb shell settings put system user_rotation 0
$adb shell svc power stayon false
```