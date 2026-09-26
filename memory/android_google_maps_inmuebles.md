---
name: android-google-maps-inmuebles
description: "Father's new Android project (separate from the stickman repo): Google Maps + Fused Location app (AlkilApp, real estate) - repo https://github.com/jh4ckt/alkilapp (public, account jh4ckt), local D:\alkilapp, v1.20 built+installed on device 2026-09-13"
metadata:
  node_type: memory
  type: project
  modified: 2026-09-16T23:20:00.000Z
---

New project brought by the user's father (2026-09-13). Source doc:
`C:\Users\jh4ck\Downloads\Proyecto_Android_Google_Maps.md` - full spec with build.gradle.kts dependencies,
AndroidManifest.xml, activity_main.xml and MainActivity.kt for an Android "ubicacion e inmuebles en tiempo
real" app using Google Maps Platform + Fused Location Provider.

**What the doc contains (starter template):**
- Deps: `play-services-maps:18.2.0`, `play-services-location:21.1.0`, compileSdk 34, minSdk 24, Kotlin,
  classic Views + Material 3 (core-ktx 1.12.0, appcompat 1.6.1, material 1.11.0, constraintlayout 2.1.4).
- UI: CoordinatorLayout + full-screen `SupportMapFragment`, floating `MaterialCardView` search bar, bottom
  sheet (`BottomSheetBehavior`, peek 100dp) with a `RecyclerView` list (`rvDepartamentos`).
- `MainActivity.kt`: runtime FINE-location permission, `fusedLocationClient.lastLocation` -> marker + camera
  animate to zoom 15f, `isMyLocationEnabled`, zoom controls.
- Dev-env table: Android Studio / AndroidIDE (on-device, open source) / Project IDX (cloud, idx.dev).

**Evaluation (reviewed 2026-09-13) - gaps to fix before building for real:**
1. **`androidx.recyclerview:recyclerview` NOT in the doc's deps** - BUT material (com.google.android.material)
   pulls RecyclerView transitively, so it actually compiles fine; still added it explicitly (`1.3.2`) in the build.
   (My first-pass claim that it "would fail to compile" was wrong - verified by a successful assembleDebug.)
2. **Search + list are UI-only shells.** Section 1 mentions enabling Places API / Geocoding API but there is
   NO Places SDK dependency (`com.google.android.libraries.places:places`), no `Places.initialize()`, no
   AutocompleteWidget wiring, no RecyclerView adapter / data source. "Inmuebles en tiempo real" is NOT
   implemented - the app only drops a marker at the current GPS position. Needs a data source (mock JSON
   list, Places Nearby Search, or a REST backend) + an adapter, and wiring the search EditText.
3. **"Tiempo real" not met:** only a one-shot `lastLocation`, which can be null on fresh install (then it
   just toasts). For real-time: `fusedLocationClient.getCurrentLocation()` or `requestLocationUpdates()`.
4. **Legacy `package=` attribute in the manifest** is deprecated - AGP 8 keyed off the `namespace` field in
   build.gradle.kts instead. Harmless but clean it up.
5. **Permission flow is minimal:** no `shouldShowRequestPermissionRationale`, no permanent-denial redirect to
   settings, only FINE requested (COARSE is declared but never requested).
6. `TU_API_KEY_AQUI` placeholder - needs a real key with Android-application restriction (package name +
   SHA-1 fingerprint) from Google Cloud Console; enable `Maps SDK for Android`, `Places API`, `Geocoding API`.

**How to apply:** this is a separate project from the stickman repo (like [[ai_game_assistant]]). If built
on this same machine, reuse the stickman Android build workflow ([[windows_dev_workflow]]): `./gradlew.bat
assembleDebug` with JAVA_HOME = `C:\Program Files\Android\Android Studio\jbr`, install via
`C:\Users\jh4ck\AppData\Local\Android\Sdk\platform-tools\adb.exe install -r <apk>`. Not yet created as a
repo/build - only the spec doc exists.

**Status 2026-09-13 (v1 built, installed, pushed):**
- Local repo: `D:\alkilapp` (OWN folder, NOT inside the stickman repo - user insisted on this). Toolchain
  cloned from stickman's android-app (gradle 8.9 wrapper jar + gradlew scripts only; AGP 8.7.2, Kotlin 1.9.24,
  compileSdk/targetSdk 36, minSdk 24). Package `com.alkilapp`, app name "AlkilApp".
- GitHub: **https://github.com/jh4ckt/alkilapp** (PUBLIC). Owner account is `jh4ckt` (email jhoner.qt@gmail.com),
  NOT ineler15 - father wanted a separate account. In 2026-09-13 gh was only logged into `ineler15`; father had
  to complete `gh auth login` device flow (browser profile "Jhoner" = the `Default` profile in Chrome, Google
  account jhoner.qt@gmail.com). `gh repo create alkilapp --public --remote origin --push` worked; default
  branch is `master`. Commits are authored as `jh4ckt <jh4ckt@users.noreply.github.com>`.
- v1 = doc's scaffold + fixes: RecyclerView wired with a `DepartamentoAdapter` + 4 sample departments (CABA),
  search EditText filters the list in place, tapping a list item centers the map/marker on it. API key NOT in
  the repo: read from `local.properties` `MAPS_API_KEY` (gitignored) and injected via manifest placeholder
  `${MAPS_API_KEY}` in `app/build.gradle.kts`.
- Verified 2026-09-13: `assembleDebug` OK, APK installed (`adb install -r`) and launched on the connected
  Lenovo device (`HGR4RJCK`) - MainActivity foreground, no FATAL EXCEPTION.
- The GitHub CLI (`gh`) MUST be used under the jh4ckt account (not ineler15) for this repo; `gh auth switch
  --user jh4ckt` if it ever reverts.

**Real Maps API configured 2026-09-13** (father: "en el proyecto debemos incluir apis de google maps, usa
la cuenta de jhoner.qt@gmail.com googlecloud"):
- Installed Google Cloud SDK on this machine via winget (`Google.CloudSDK` 584.0.0) at
  `C:\Users\jh4ck\AppData\Local\Google\Cloud SDK\google-cloud-sdk\bin\gcloud.cmd` (was NOT on PATH; this is
  the path to call directly). Logged in as **jhoner.qt@gmail.com** via interactive `gcloud auth login`
  (auto-opened browser; father completed the click-through).
- **There is ALREADY a GCP project named "Alkilapp": id `gen-lang-client-0040505884`** (auto-generated id from
  the old gen-lang creation flow). Used THAT instead of creating a new one. (The account also has ~11 other
  gen-lang-client projects, the per-character stickman Gemini quota-isolation projects.)
- Enabled on that project: `maps-android-backend.googleapis.com`, `geocoding-backend.googleapis.com`,
  `places-backend.googleapis.com` (i.e. Maps SDK for Android, Geocoding API, and the NEW Places API - returned
  in one async `services enable` op).
- Created RESTRICTED API key **`AIzaSyCidZ9XXN69qaqa_TdLhv1YTLYE-1AWsCs`** (name
  `projects/288429280561/locations/global/keys/eaba5ec0-cd33-4790-b47d-c42316fdae46`, displayName "AlkilApp
  Android") via REST POST+operation-poll (apikeys.googleapis.com v2), NOT gcloud (gcloud key create doesn't
  set Android restrictions). Restrictions verified via GET: only `com.alkilapp` + SHA-1
  `b4ed9dc44203e58db68eb745fc7beda902cc879f` (= `B4:ED:9D:C4:42:03:E5:8D:B6:8E:B7:45:FC:7B:ED:A9:02:CC:87:9F`,
  debug keystore) + only the 3 enabled services. If a release build is ever signed with a real keystore, that
  SHA-1 must be added to the restriction or the key will 403 for the release APK.
2026-09-16 (v1.20, commit 97d4e80 + 15eb064): **Rediseño de UI completo**. Nueva paleta azul/blanco/oscurita (`colors.xml` con `alkil_primary #1B72E8`, `alkil_primary_dark #1E40AF`, aliases de compatibilidad para coral/rojo/old colors). `activity_main.xml` reescrita: barra superior SOLO con búsqueda (`cardBusqueda` + `etBusqueda`, sin btnFiltros/btnChat/btnAuth), **sidebar izquierda** (52dp, `bg_sidebar.xml`, 3 items: Perfil/Chat/Filtros con iconos + labels), `overlayBadges` FrameLayout sobre el mapa, FAB `fabAgregar` (top) + `fabMiUbicacion` (bottom-end). `MainActivity.kt` reescrita con: `configurarSidebar()` (handlers), sistema de badges (`configurarBadges` con `setOnCameraIdleListener`, `actualizarBadges(lista)` agrupa por `barrio`/`ciudad`, `posicionarBadges` con `mMap.projection.toScreenLocation`, `construirBadge` = MaterialCardView con dot verde + "zona · N"), filtro feed excluye `finalizado`. Build OK, instalado, badges visibles (`Miraflores · 1`, `Surco · 2`, `San Miguel · 1`) sobre el mapa con "Tu Ubicación" dot. Push a GitHub con release v1.20 draft (`gh release create v1.20`). Pendientes: publicar release (quitar --draft), iOS AlkilApp.
- What's STILL missing (unchanged from the gap notes above): Places SDK dependency + wiring (search bar and
  RecyclerView are wired to a mock list, not Places Nearby/Autocomplete; "inmuebles en tiempo real" still
  needs real data), realtime location (`requestLocationUpdates`/`getCurrentLocation` instead of one-shot
  `lastLocation`), Places-backed data source for the department list.

**2026-09-16 (same session, commit `d75fdc3`):** refinamientos UI enlazados al mapa pedidos por el padre.
`activity_main.xml` reescrita: la sidebar de 3 ítems se reemplazó por un **solo `btnMenu` (hamburguesa,
46dp, arriba-izquierda) con `PopupMenu`** (`menu/menu_principal.xml`, items Perfil/Chat/Filtros = las mismas
acciones `mi_perfil_titulo`/`chat_btn`/`filtros_btn`); `cardBusqueda` bajo el menú (marginTop=66dp);
`fabAgregar` queda debajo de la búsqueda (`layout_gravity="end|top"`, marginTop=124dp, SIN anclaje);
`fabMiUbicacion` SIN ancla (`bottom|end`, marginBottom=108dp) y **sube junto con el bottomSheet** via
`BottomSheetBehavior` (`translationY = -slideOffset*(sheet.height-peekHeight)`); `overlayBadges` full-screen.
`MainActivity.kt`: `configurarSidebar()` → `configurarMenu()`; `abrirDetallePropiedad` hace pan al inmueble
(`animateCamera(newLatLngZoom(ubicacion, 16f))`); `mMap.uiSettings.isMyLocationButtonEnabled=false` (el botón
nativo de Google chocaba con la topbar); `actualizarUiSesion()` quedó no-op. `PropiedadAdapter` ya ordena por
distancia (`sortedWith(compareBy{it.esDestacado}.thenBy{distanciaKm})`). Verificado en vivo por uiautomator:
btnMenu [34,23][163,152], cardBusqueda [45,186][1035,322], fabAgregar [866,349][1024,507], fabMiUbicacion
[866,1938][1024,2096] colapsado (sin solapes), bottomSheet colapsado [0,2119][1080,2400].

**2026-09-16 (same session, commit `508297e`):** **botón "Ver ubicación en mapa" en la ficha del inmueble**
(pedido literal del padre: "cuando el usuario ingrese a la información del inmueble debe tener un boton que
le diga 'ver ubicación en mapa' y llevarlo a las coordenadas según la dirección registrada"). En
`activity_propiedad_detalle.xml`, nuevo `btnVerMapa` (MaterialButton 52dp, `alkil_primary`, `ic_location.xml`
nuevo vector, texto `detalle_ver_mapa`) entre la tarjeta de info y `btnChatPropietario`. **Nueva actividad
`UbicacionInmuebleActivity`** (`UbicacionInmuebleActivity.kt` + `activity_ubicacion_inmueble.xml`, registrada
en manifest): cabecera con volver + título, `SupportMapFragment` (`mapaInmueble`) a pantalla centrado en
`lat/lng` (zoom 16) con marcador del inmueble, y botón al pie **"Cómo llegar"** (`google.navigation:q=lat,lng`,
con toasts de error). La ficha pasa extras `EXTRA_LAT/LNG/TITULO` (`det_lat`/`det_lng` son float, NO `--ed`
en am start); `btnVerMapa` se oculta (GONE) si lat/lng son 0. Verificado en vivo: ficha real de Firestore
("Departamento sin amoblar en Santiago de Surco" de Diego Flores) mostró `btnVerMapa` en [45,1315][1035,1461]
y `UbicacionInmuebleActivity` tomó `mCurrentFocus` sin FATAL. Nota de debugging: `adb shell am start` con
extras que contienen espacios/paréntesis rompe el shell del device ("syntax error: unexpected '('") — pasar
el comando completo como UN string con comillas internas, y las actividades `exported="false"` no pueden
lanzarse desde shell sin marcar temporalmente `exported="true"` (se revirtió antes del commit). APK final
instalado en el Xiaomi (versionName sigue 1.20; sin release nuevo aún). Sería deseable publicar release v1.20
(quitar --draft) y/o bump a v1.21 para esta tanda.

**2026-09-16 (same session, commit `bd7be47`): buscador = filtro único.** Pedido del padre: "el label de
busqueda no funciona, debe ser de filtración, fusionalo con el filtro de busqueda y retiralo de la botonera
tipo hamburguesa". El buscador SOLO filtraba por texto en el adapter (lista invisible detrás del bottomSheet
colapsado → "no funciona"); el filtro distrito/departamento vivía en el diálogo `abrirDialogoFiltros()` del
menú hamburguesa. Cambios: (1) `menu_principal.xml` quedó SOLO con Perfil + Chat (se quitó `menuFiltros`);
(2) **embudo `ic_filter.xml` dentro de la barra de búsqueda** (`btnFiltroBuscar`, 40dp, al final de
`cardBusqueda`) que abre `abrirDialogoFiltros()`; (3) texto filtra por `titulo/direccion/barrio/tipo/ciudad`
(adapter + helper `propiedadCoincideTexto`) y `doAfterTextChanged` ahora juga `actualizarZonaMapa(false)` →
**el mapa panea a los resultados** (`latLngBounds` de `adapter.visibles()`, o se recentra en el usuario si
queda vacío el texto); (4) `actualizarZonaMapa(conMensaje=true)` generalizada (hayFiltro = distrito ||
departamento || búsqueda; el toast de "sin resultados" solo en aplicar-filtros, no por tecla); (5) "Limpiar
filtros" ahora también limpia `etBusqueda`; (6) `actualizarBotonFiltros()` tiñe el embudo `alkil_primary`
cuando hay filtro/distrito/búsqueda activa vs `text_secondary`; (7) hint nuevo "Buscar por inmueble, distrito
o zona…"; `fabAgregar` bajó a marginTop=148dp (el embudo agrandó la barra y lo solapaba). Verificado en vivo
por uiautomator: embudo [888,220][1001,333] dentro de barra [45,186][1035,367], fabAgregar [866,416] (sin
solape), y el padre probando en vivo abrió el diálogo "Filtrar inmuebles" tocando el embudo (confirma que
abre). APK final instalado.

**2026-09-16 (same session, commit `f7bd672`): fila superior alineada y bajada.** Pedido: "alinea el menu
hamburguesa con el filtro, para que no quede vacio" + "baja un poco mas la barra y el menú esta muy arriba".
`activity_main.xml`: `btnMenu` (46dp) y `cardBusqueda` se metieron en un **LinearLayout horizontal**
(`layout_gravity="top"`, `gravity="center_vertical"`, `cardBusqueda` width=0dp con weight=1 y marginStart=10dp)
y el margen de la fila quedó en **marginTop=30dp** (antes 66dp del card, que lo pegaba a la barra de estado).
Verificado por uiautomator: btnMenu [34,110][163,239], cardBusqueda [191,84][1035,265] (ya no hay card por
encima del menú), fabAgregar [866,416][1024,574] sin solape.

**2026-09-16 (same session, commit `0bb0fcd`): RESEÑAS AL PROPIETARIO (estrellas 1-5).** Pedido: "debemos
agregar la opción de dejar reseña al propietario y esta a su vez pueda reflejar el puntaje de califación en
sistema de puntos hasta 5". El lado LECTURA ya existía (subcolección `usuarios/{uid}/reviews` leída en
`PerfilPropietarioActivity.kt` — doc con `authorName`/`rating`/`comment`/`createdAt`; y el agregado
`rating`/`totalRatings` del doc `usuarios/{uid}` mostrado en la cabecera del perfil, en
`PropiedadDetalleActivity` (`tvDetOwnerRating`) y en `ChatDetailActivity` (`tvChatRating`)). Lo que se agregó:
(1) **`dialog_resena.xml`** nuevo (subtítulo + `llResenaEstrellas` + `etResenaComentario` multilinea);
`abrirResena()` en `PerfilPropietarioActivity` infla el diálogo, crea 5 `ImageView` de 38dp con `ic_star`
tintados oro/gris (`pintarEstrellas`), valida `seleccion<=0` con toast (por eso el `setOnShowListener` con
`getButton(BUTTON_POSITIVE)` en vez de `setPositiveButton`, para no cerrar el diálogo sin nota) y guarda;
(2) **`guardarResena()`**: calcula el promedio leyendo las reseñas existentes (excluye la propia por id, para
poder recalificar), escribe `usuarios/{uid}/reviews/{miUid}` (doc id = **uid del autor** → una reseña por
usuario, recalificar la actualiza) con `authorId/authorName/rating/comment/listingId/createdAt` + `update` del
doc del propietario con `rating` (promedio redondeado a 1 decimal) y `totalRatings`, todo en un `batch`;
(3) botón `btnPerfilResena` ("Escribir reseña", OutlinedButton con `ic_star` dorado) en la cabecera del perfil,
visible SOLO si `!esMio` y uid no vacío. Strings nuevas `resena_*` en voseo.
4) **Reglas Firestore nuevas (ruleset `4d1b11fd-77e4-472b-b4de-9a99a016ced4`, desplegado a los 2 releases
`cloud.firestore` y `cloud.firestore/alkilappdb`)**: `usuarios/{usuario}` ahora permite `update` a un
autenticado NO dueño SOLO si `request.resource.data.diff(resource.data).affectedKeys().hasOnly(['rating',
'totalRatings'])` (excepción para el agregado de reseñas; el dueño sigue editando su doc completo);
`usuarios/{usuario}/reviews/{review}` `create` ahora exige `authorId == request.auth.uid` y `rating` numérico
1..5 (antes era `request.auth != null` a secas) y el `update/delete` exige que `authorId` siga siendo del
autor. Se desplegó con SA `alkilapp-seed` vía `firebaserules.googleapis.com` (POST ruleset → DELETE+POST de
cada release; ojo: el release de la 2ª base lleva `/` → encodeURIComponent del path tras `releases/`).
5) **Verificado END-TO-END en vivo**: el padre tocó "Escribir reseña" en el perfil de Renzo Salazar
(`u-propietario-1`), el diálogo se abrió (uiautomator vio las 5 estrellas con `content-desc` "Calificación N de
5", el campo de comentario y Cancelar/Publicar) y publicó: quedó `usuarios/u-propietario-1/reviews/
smpJfxQT0vUMaCMalJWUej1qPnw1` = `{authorName:"Jhoner H. Quispe Tafur", authorId:smpJfxQT0…, rating:5,
comment:"Todo genial", listingId:"9au4v68QJhLgMQB5p0Wn"}` y el doc del propietario pasó a **`rating=4.7`,
`totalRatings=3`** (promedio real de los 3 docs) sin PERMISSION_DENIED ni FATAL. **OJO/dato a tener en cuenta:**
los usuarios del seed tenían `totalRatings` inflado (Renzo decía 12 con solo 2 docs de reseña); el recálculo
lo corrige a la cuenta real (3) — es intencional (promedio honesto desde los documentos reales), pero el
numero baja respecto del seed la primera vez que alguien reseña a un usuario seed.
**Nota de método:** `ui_low.xml`/`ui_row.xml` y los scripts temporales `backend/_tmp_*.js` (dump de reglas,
deploy de reglas, query de usuarios) se borraron antes del commit; el `parse_ui.ps1` sigue en
`C:\Users\jh4ck\AppData\Local\Temp\opencode\`.

**Pendientes:** publicar el release v1.20 (sigue en draft en GitHub; decidir si v1.20 o bump v1.21 con
resenas+mapa+buscador), iOS AlkilApp (pedido por el padre), y los gaps de siempre (Places SDK/data real,
`requestLocationUpdates`).