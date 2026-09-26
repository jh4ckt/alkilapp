---
name: android-alkilapp-firestore-base
description: "AlkilApp database foundation: Firestore usuarios+propiedades collections, security rules, budget alert for free mode. FIXED 2026-09-14: named-DB rules release + Firebase Auth provisioning + Android app wired (reads/writes/login working live). Google Sign-In DONE E2E on Xiaomi (2026-09-15). Added fotos (base64) + distance sort, native chat (inbox+conversation), featured/verification badges, property detail ficha with gallery + deterministic per-listing chat, denuncia + owner profile, rules v5 (a220c618). Ronda mapas: filtro distrito mueve el mapa a la zona, botón 'mi ubicación', alta como formulario (RegistrarPropiedadActivity, no pop-up), 'habitación' nuevo tipo. Backend: seed/queries.js + verificacion.js (email code) + moderacion.js (reports→under_review). Pending: GitHub push/release, iOS, live test of the map round. RONDA 16 (2026-09-18): fix listado invisible (bottom sheet off-screen por estado guardado viejo que sobrevivia install -r) + selector de ubicacion en el mapa al publicar. Android v1.30 (vc12)."
metadata:
  node_type: memory
  type: project
  modified: 2026-09-14T00:00:00.000Z
---

Father asked 2026-09-13/14: "debemos crear una base para el registro de usuarios y registro de
propiedades, revisa las bases creadas en el google cloud, siempre validando que todo el proyecto se
haga en modo gratuito." Context: AlkilApp ([[android_google_maps_inmuebles]]) needed its data
layer. gcloud was installed this session (see that memory) and logged in as jhoner.qt@gmail.com.

**Review of all existing DBs across the account's 11 projects (scan done via gcloud):**
- ONLY ONE database exists: Firestore **`alkilappdb`** (FIRESTORE_NATIVE, named DB, location `nam5`)
  in project `gen-lang-client-0040505884` (the "Alkilapp" GCP project). It was created by a previous
  Firebase onboarding (NOT by me) - the project has the WHOLE Firebase suite enabled already
  (firebase, firebaserules, auth/identitytoolkit, storage, hosting, fcm, installs, remoteconfig,
  appengine, datastore, bigquery, etc.) and a NAMED db, which is what forced it onto the paid
  **Blaze** plan with a real billing account (`015CBB-36D20E-E5BA10` "Mi cuenta de facturación",
  jhoner is owner). The `(default)` database does NOT exist.
- The other 10 projects (stickman per-character quota projects) have NO databases, all billing off.

**Free-mode decision (important nuance, do NOT unlink billing):**
- **Google Maps Platform REQUIRES billing to be active** - unlinking billing would break the Maps
  API key we configured. The $200/month Maps credit covers hobby usage, so it stays free in practice.
- Firestore free daily quota (1GB storage, 50K reads, 20K writes, 20K deletes/day) is a HARD cap per
  project on the default tier - exceeding it returns errors, it does NOT auto-bill. Firebase Auth is
  free to 50K active users/month. Cloud Storage (firebasestorage enabled) has a free tier too.
- Since Google removed hard spending limits in 2021, the safety net is a **budget alert**: created
  `AlkilApp-alerta-gastos` on billing account `015CBB-36D20E-E5BA10` amount 1 (account currency -
  NOT USD!), thresholds 100% CURRENT_SPEND + 50% FORECASTED_SPEND, filtered to project
  288429280561, default IAM recipients get the email. Verified live via REST (id
  `1f3eae1a-4842-492c-8f7c-8a9bc56d2663`).
- **API trap hit twice**: the budgets REST API rejects `currencyCode: "USD"` (billing account is not
  in USD) with generic INVALID_ARGUMENT - omit currency and it works. gcloud flag trap: it's
  `--budget-amount` / `--filter-projects` (not `--amount`/`--budget-filter-project`), and the CLI
  still 400'd; did it via REST with `x-goog-user-project` header instead.

**Firestore foundation built in `alkilappdb` (2026-09-14, all via REST with Bearer token):**
- Collection **`usuarios`** (doc `usuarios/_plantilla` as deletable schema template): `nombre`,
  `email`, `telefono`, `tipoUsuario` (dueno/inquilino/interesado/admin), `uidAuth` (Firebase Auth UID
  when the app wires in), `activo` (bool), `fechaRegistro` (timestamp).
- Collection **`propiedades`** (doc `propiedades/_plantilla`): `titulo`, `descripcion`, `tipo`
  (departamento/casa/terreno/local/comercial), `operacion` (venta/alquiler/temporada), `precio`,
  `moneda`, `direccion`, `barrio`, `ciudad`, `lat`, `lng`, `imagenUrl` (array), `idPropietario`,
  `contacto`, `estado` (disponible/reservado/alquilado/vendido), `ambientes`, `superficieM2`,
  `comodidades` (array), `publicadoEn` (timestamp).
- **Security rules released** to `projects/.../releases/cloud.firestore` (ruleset
  `34a6ca44-7169-451e-9cbc-97f87f6bcf23`): `propiedades` readable by anyone, writes only for
  authenticated users; `usuarios` per-user access via `request.auth.uid == resource.data.uidAuth`
  (and create via `request.resource.data.uidAuth`); catch-all `match /{document=**} deny`. Re-deploy
  the same pattern on any new collection or it stays locked by the catch-all deny.
- **Firebase Auth**: identitytoolkit API enabled; email/password provider enablement status NOT
  verified (the identitytoolkit getConfig/updateConfig REST endpoints 404 on this project - needs
  the Firebase console or app wiring to confirm; likely on by default). The `usuarios.uidAuth` field
  + rules assume Auth is eventually wired on Android.

**REST cheat-sheet used this session** (gcloud `auth print-access-token` + `x-goog-user-project:
gen-lang-client-0040505884` header):
- Firestore commit: `POST https://firestore.googleapis.com/v1/projects/{p}/databases/alkilappdb/documents:commit`
  with `{writes:[{update:{name, fields}}]}` - note PowerShell/ConvertTo-Json mangles nested
  hashtable arrays; use a raw JSON string payload.
- List: `GET .../documents/{collection}`.
- Rulesets/releases: `POST firebaserules.googleapis.com/v1/projects/{p}/rulesets` then
  `/releases` with `{"name":"projects/{p}/releases/cloud.firestore","rulesetName":...}`.
- Budgets: `POST billingbudgets.googleapis.com/v1/billingAccounts/{acct}/budgets` (no currency /
  project resource name `projects/288429280561`).

**Next steps (mostly done as of 2026-09-14):** the Android app `com.alkilapp` was registered in the
Firebase project (google-services.json), and sign-up/sign-in + write/read of `usuarios`/`propiedades`
is WIRED AND WORKING LIVE (see below). Remaining: nothing critical for the base; **iOS version of
AlkilApp requested by the father, left PENDING** (deferred, not started).
Remember to keep every usage inside the daily free quotas (the "free mode" constraint the father repeated).

---

## THE BIG FIX (2026-09-14): named-DB rules release + Auth provisioning

Two root causes explained ALL the launch-time failures (`PERMISSION_DENIED` on reads AND writes, then
`CONFIGURATION_NOT_FOUND` on Auth) and neither was an app-code bug:

1. **A named database has its OWN rules release.** Listing releases showed THREE, not one:
   `releases/cloud.firestore`, **`releases/cloud.firestore/alkilappdb`** (ruleset
   `7a461af8-f21e-46c1-8110-333c4ac11bd6`, created 2026-09-07 = the DB creation date), and
   `releases/firebase.storage/...`. The SDK connects to `alkilappdb`, so the effective rules came
   from `cloud.firestore/alkilappdb` — which contained a **deny-all**
   (`match /{document=**} allow read, write: if false`) left over from DB creation. Every rules
   edit we had published to `releases/cloud.firestore` (strict `34a6ca44`, then permissive
   `2cb1cd45`) was simply ignored by the named DB. This is why reads AND writes both
   PERMISSION_DENIED even under "permissive" rules.
   **Fix:** published the intended PRODUCTION strict ruleset (ruleset `d29944e0-...`, source in the
   session scratch / below) and PATCHed BOTH releases to it.
2. **Firebase Auth was never provisioned** (APIs enabled ≠ backend configured): sign-up failed with
   `CONFIGURATION_NOT_FOUND`. Fixed via REST:
   `POST https://identitytoolkit.googleapis.com/v2/projects/{p}/identityPlatform:initializeAuth`
   (empty body) → "INITIALIZE OK"; then
   `PATCH .../v2/projects/{p}/config?updateMask=signIn.email.enabled,signIn.email.passwordRequired`
   with body `{"signIn":{"email":{"enabled":true,"passwordRequired":true}}}`
   (maybe `updateMask=signIn.email.enabled` suffices) → email/password ON. GET `/config` worked
   here (unlike the earlier 404s), and showed signIn.email.enabled blank before the fix.

**Final security rules now live (ruleset `d29944e0-9ace-4a57-9862-9393ec308d08`, on BOTH releases):**
```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /propiedades/{propiedad} {
      allow read: if true;
      allow create: if request.auth != null;
      allow update, delete: if request.auth != null;
    }
    match /usuarios/{usuario} {
      allow create: if request.auth != null && request.auth.uid == request.resource.data.uidAuth;
      allow read, update, delete: if request.auth != null && request.auth.uid == resource.data.uidAuth;
    }
    match /{document=**} { allow read, write: if false; }
  }
}
```

**Release-update API gotcha (REST, wasted two attempts):**
- `POST firebaserules.../releases` only CREATES — returns 409 Conflict if the release already exists.
- PATCH with a FLAT body `{"name":..., "rulesetName":...}` returns 400 "Unknown name `rulesetName`".
- The WORKING shape is nested + updateMask:
  `PATCH .../releases/{name}` with
  `{"release":{"name":"projects/{p}/releases/...","rulesetName":"projects/{p}/rulesets/{id}"},"updateMask":"ruleset_name"}`
  (headers: `Authorization: Bearer` + `x-goog-user-project: gen-lang-client-0040505884`).
- The dev `gcloud-firestore-rules.ps1`/`-perm.ps1` scratch scripts only do the POST path; the
  working PATCH calls were done inline. If reusing them, add the nested-PATCH step for the
  `cloud.firestore/alkilappdb` release too.

**Other launch-time noises explained (benign):**
- `GoogleApiManager: Unknown calling package name 'com.google.android.gms'` SecurityException =
  internal GmsCore noise, does NOT break anything. The father's device also has toasts suppressed
  for the app ("Suppressing toast from package com.alkilapp"), so error popups may not appear.
- REST API-key tests from PowerShell (no Android app context) return 403
  `API_KEY_ANDROID_APP_BLOCKED` for the restricted `AIzaSyCidZ9XXN69qaqa_TdLhv1YTLYE-1AWsCs` key —
  EXPECTED, proves the key restriction works, not a failure.

**Live verification (father's tablet `HGR4RJCK`, Lenovo):** Auth registers real users (father
created `jeremy.qr2602@gmail.com`); after the rules fix, launch logcat is CLEAN (no PeListen/Write/
PERMISSION_DENIED) → `propiedades` read works. **Caveat found mid-fix:** the `usuarios` document
for that account is MISSING because the write fired while the deny-all rules were still live, and
the code only created the doc on REGISTER, not on LOGIN. Fixed by calling `guardarUsuarioEnBase()`
in the login-success path too (idempotent set()). Rebuild/install happened; the father still needs
one more in-app pass ("Ingresar", then publish a property) to backfill the doc and confirm end-to-end.

**Android app wiring (this session, all committed in D:\alkilapp):** Gradle root+app:
`com.google.gms.google-services` 4.4.2 (apply false root, apply app), BoM `firebase-bom:33.1.1` +
`firebase-firestore` + `firebase-auth`. New Kotlin: `data/Propiedad.kt`,
`ui/PropiedadAdapter.kt`; `MainActivity.kt` rewritten (register/login via email+password,
`db = FirebaseFirestore.getInstance("alkilappdb")` — using the DEFAULT `getInstance()` hit the
`(default)` DB which does NOT exist → NOT_FOUND, another old bug, snapshot listener ordered by
`precio`, `fabAgregar` publishes properties with lat/lng from the map center). New layouts/drawables/
strings incl. `item_propiedad.xml`, `dialog_auth.xml`, `dialog_propiedad.xml`, `ic_person.xml`,
`ic_add.xml`. Deleted old `Departamento.kt`/`DepartamentoAdapter.kt`/`item_departamento.xml`.
Build warns `compileSdk 36` vs AGP 8.7.2 (harmless; can silence with
`android.suppressUnsupportedCompileSdk=36` in gradle.properties).

---

## Feature round 2 (2026-09-14): moneda, filtros Lima, Google Sign-In (WIP)

Father asked: currency dropdown (soles/dólares), show user location on start (ALREADY had it via
FusedLocation + runtime permission from round 1), filters by department/district for Peru/Lima, and
login via Google accounts.

**Shipped + built + installed (logcat clean; pending live visual confirmation, device was locked):**
- **Moneda**: `spPropMoneda` spinner in the publish dialog (`monedas` array "Soles (PEN)"/"Dólares
  (USD)"), stored as `PEN`/`USD` in the `moneda` field; `Propiedad.precioFormateado` now maps
  PEN→"S/ " and everything else→"$ " (was showing the raw "USD" code). Also fixed the price hint
  "Precio (USD)" → "Precio".
- **Publicar en Lima**: `spPropDistrito` spinner (43 districts of Lima Metropolitana in
  `distritos_lima`, first option "Sin distrito"); on save writes `barrio=<distrito>` and
  `ciudad="Lima"` so published props match the filters. NOTE: the city is hardcoded to Lima per the
  current scope.
- **Filtros**: `btnFiltros` (text button in the search card) opens `dialog_filtros.xml` with
  department spinner (`departamentos_peru` = just "Lima" for now, prefixed with "Todos") + district
  spinner (`Todos` + 43). Applies via `adapter.setFiltros(departamento, distrito)`; the button label
  shows the active filter ("Filtros: Miraflores"); a "Limpiar filtros" neutral button clears.
  `PropiedadAdapter` now combines the text search + department (matches `ciudad`) + district
  (matches `barrio`). Matches are case-insensitive and "Todos"/null = no constraint.
- **Demo data re-seeded to Lima** (`gis-seed-firestore-lima.ps1`): the same 6 doc ids updated to
  Miraflores/San Isidro/Barranco/Santiago de Surco/La Molina/Cercado de Lima with mixed PEN/USD
  prices and real Lima coords — so filters have data to show. Admin REST writes bypass rules, so no
  auth needed to reseed.

**Google Sign-In — DONE server-side, E2E unconfirmed (2026-09-14 night):**
- Code complete and builds: `com.google.android.gms:play-services-auth:21.2.0`; classic flow
  (`GoogleSignInClient` + `registerForActivityResult`); button "Ingresar con Google" in
  `dialog_auth.xml`; on success signs in to Firebase with the Google ID token and backfills
  `usuarios` (same `guardarUsuarioEnBase()`, which now ALSO runs on plain email login to backfill
  docs created before rules allowed it).
- **Provider + web client now exist** (father enabled Google in the Firebase console — one toggle —
  no API could do it). Extracted the client id via `accounts:createAuthUri` with
  providerId=google.com → the response `authUrl` carried
  `client_id=288429280561-0f7jlpoc8h91m7naj0gsstavj8co4t38.apps.googleusercontent.com`. Embedded in
  `<string name="default_web_client_id">` in strings.xml, rebuilt, installed.
- Probe knowledge that cost time: bogus `signInWithIdp` gave `INVALID_CREDENTIAL_OR_PROVIDER_ID`
  even when provider was DISABLED → red herring; `createAuthUri` (which returns `OPERATION_NOT_ALLOWED`
  when not configured and an `authUrl` with `client_id` when configured) is the reliable probe.
- **E2E test on the tablet was INCONCLUSIVE**: after sign-out + tapping the Google button the app
  came back signed in as `jeremy.qr2602@gmail.com` but the `usuarios` collection still had only ONE
  doc (`5bDFqv0wx5b7cJXxNyZweTwpK5H2` = the old email/password uid). Google sign-in would create a
  SECOND uid+doc, so either sign-out never took or Google never completed. Re-test on the new phone
  (fresh device, no session) is the definitive check.
- Firestore REST gotcha (wasted time): this project has NO `(default)` database — only the named
  `alkilappdb`. `databases/(default)/documents:runQuery` → 404 "database (default) does not exist".
  Use `databases/alkilappdb`. `ListDocuments` also 404'd; `runQuery` works.
- Build notes: `GoogleSignIn`/"GetSignedInAccountFromIntent" are deprecated in play-services-auth
  but functional and the established pattern; fine to keep.
- iOS (AlkilApp) remains PENDING (father requested, not started).

**Google Sign-In — CONFIRMADO END-TO-END en el teléfono Xiaomi (2026-09-15):**
- El padre tocó "Ingresar" → "Ingresar con Google" → eligió su cuenta **`presupuestos@oleohidraulic.com`**
  (la del teléfono, distinta de la de email) → Firebase firmó con el ID token y `guardarUsuarioEnBase()`
  creó el doc. `usuarios` ahora tiene **2 docs**: `5bDFqv0wx5b7cJXxNyZweTwpK5H2` (jeremy@…, email/password)
  y `smpJfxQT0vUMaCMalJWUej1qPnw1` (presupuestos@…, id de Google). El uid del doc de Google es el
  `idPropietario`/`contacto` de las propiedades que publique ese usuario. La ambigüedad de la tablet se
  resolvió: el flujo Google completo funciona (client_id de createAuthUri + requestIdToken + GoogleAuthProvider).
- La huella SHA-1 del keystore de debug (`apksigner verify --print-certs` en app-debug.apk =
  `b4ed9dc44203e58db68eb745fc7beda902cc879f`) coincide EXACTAMENTE con la registrada en Firebase
  (androidApps API) → el registro de la app siempre estuvo correcto.
- **MIUI/HyperOS del Xiaomi 24116RACCG bloquea la inyección de entradas por adb** (`adb shell input/keyevent`
  → `SecurityException: INJECT_EVENTS`) AUNQUE "Depuración USB (Ajustes de seguridad)" esté `checked=true`
  (verificado en el dump). La solución práctica: **el padre toca, el agente lee** (uiautomator dump +
  coordenadas). No perder tiempo re-intentando input tap en este ROM.
## Feature round 3 (2026-09-15): fotos en el alta + lista ordenada por cercanía — CONFIRMADO EN VIVO en el Xiaomi

- **Fotos (hasta 3 por inmueble):** `Propiedad.fotos: List<String>` (base64). En el alta
  (`dialog_propiedad.xml`: `btnPropAgregarFoto` + `llPropFotos` en HorizontalScrollView) se elige
  con `PickMultipleVisualMedia()` de AndroidX (photo picker). `comprimirFoto()` decodifica con
  `ImageDecoder` (API 28+, respeta EXIF; fallback `BitmapFactory`), escala a máx 900px y guarda
  JPEG q55 → cada foto ~150-250KB base64 (3 fotos caben en el límite de 1 MiB por documento
  Firestore). `renderizarPreviewsFotos()` muestra miniaturas tappables (tocar = quitar). Se
  guardan en el doc como campo `fotos` (el viejo `imagenUrl` queda vacío).
- **Inmuebles cercanos:** `PropiedadAdapter` ganó `setUbicacion(lat,lng)`: filtra igual que antes
  (texto + departamento + distrito) y luego ordena por distancia havrsine `distanciaKm()` (las
  propiedades sin lat/lng van al final). `item_propiedad.xml` rediseñado: miniatura `ivFoto`
  (72dp, `bg_foto_thumb.xml`, `decodificarThumb()` con `LruCache` 8MB) + chip `tvDistancia`
  ("850 m" / "1,2 km"). `MainActivity.obtenerUbicacionActual()` ahora pide ubicación FRESCA con
  `getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)` y cae a `lastLocation` si falla
  (antes solo `lastLocation`, que en la tablet devolvía null → nunca ordenaba).
- New strings: `prop_fotos_*`, `prop_distancia_km/m`, `prop_foto_thumb_cd`.

**Estado al cierre (2026-09-15): casi TODO confirmado en vivo en el Xiaomi:**
- `adb install` funciona ("Instalar vía USB" ON). "Depuración USB (Ajustes de seguridad)" está ON
  (`checked=true`) pero **aun así `input tap`/`keyevent` dan `SecurityException: INJECT_EVENTS`** —
  inyección remota no disponible en este ROM; flujo de trabajo = el padre toca, el agente lee.
- Google Sign-In ✅, fotos (2) ✅, lista por cercanía con distancias reales ✅, alta como usuario Google ✅.
- Demo doc de prueba: `propiedades/g8dDraNzw77DAgPZWMZ0 | Cuarto | fotos=2` (borrar cuando se quiera).
- **Pendiente:** decisión del padre sobre push a GitHub (`jh4ckt/alkilapp`) + releasear; **iOS de AlkilApp PENDIENTE** (solicitado, no iniciado).

---

## Backend + seed (2026-09-15): primer backend Node + datos de prueba "fusionados"

El padre pidió "actualizar el front y back end" con un spec nuevo completo (colecciones
`users`, `listings`, `reviews`, `chats` con campos ricos) y, ante la disyuntiva de romper la app
en vivo, optó por **"Fusiona, actualiza de acuerdo a lo que tú consideres mejor"**. Decisión tomada:
**la app sigue leyendo SOLO `usuarios`+`propiedades`**, y el esquema nuevo se fusionó encima de
esas mismas colecciones (campos nuevos coexistiendo con los que ya leía la app) + colecciones NUEVAS
para lo que la app aún no usa (`chats`, `reviews`).

**Nuevo: `backend/` en D:\alkilapp (Node + @google-cloud/firestore v7).**
- `seed.js` — inicializa/carga datos de prueba de Lima en `alkilappdb` (idempotente: borra SOLO IDs
  fijos `seed-*` y los 6 demos viejos; NO toca "Cuarto"/"Mini departamento" del padre).
- `package.json` (script `npm run seed`), `README.md` (instrucciones credenciales), `.gitignore`
  (excluye `node_modules/` y `credentials/`).
- **Credenciales**: service account `alkilapp-seed@gen-lang-client-0040505884.iam.gserviceaccount.com`
  (rol `roles/datastore.user`), key en `backend/credentials/alkilapp-seed-sa.json` (gitignored). El
  cliente apunta SIEMPRE con `{ projectId: 'gen-lang-client-0040505884', databaseId: 'alkilappdb' }`
  (base nombrada; el proyecto no tiene `(default)`). Env `GOOGLE_APPLICATION_CREDENTIALS` también vale.
- Datos cargados (verificado vía SDK):
  - `usuarios`: `u-propietario-1` (Renzo Salazar, rol propietario, rating 4.8), `u-inquilino-1`
    (Valeria Quispe), `u-ambos-1` (Diego Flores) — cada uno con `verification`, `phoneNumber` +51,
    `rating`/`totalRatings`, `profilePicture`, `createdAt`. Los 2 usuarios reales del padre NO se tocan.
  - `propiedades`: `seed-l-hab-miraflores` (habitación co-living, S/950, roommatePreferences),
    `seed-l-depa-sanmiguel` (depa amoblado, S/1800), `seed-l-depa-surco` (sin amoblar, S/1400),
    `seed-l-casa-surco` (casa 2 pisos, S/3200). Cada doc = campos app (`titulo`, `precio`, `moneda`,
    `barrio`, `lat/lng`, `fotos=[]`, `comodidades`, …) **+** campos spec (`propertyType`, `rentalType`,
    `country PE`, `city Lima`, `district`, `features`, `location{lat,lng,address}`, `geohash` calculado,
    `photos` urls picsum, `ownerId`, `viewsCount`, `status`, `createdAt/updatedAt`). Se muestran solos en
    la lista de la app (sin miniatura: `fotos` vacío → `ivFoto` GONE).
  - `usuarios/u-propietario-1/reviews/`: rev-renzo-01 (5★) y rev-renzo-02 (4★).
  - `chats/chat-demo-01` (listingId=seed-l-hab-miraflores, participants inquilino+propietario,
    `unreadCount` por participante, `lastMessage`/`lastMessageAt`) + 3 `messages`.
- **Rules v2**: ruleset `7097e229-887a-4f6c-bf46-7688432818c6` desplegado en los DOS releases
  (`cloud.firestore` y `cloud.firestore/alkilappdb`). Cambios vs v1 (d29944e0): lectura de `usuarios`
  pasa a pública, regla de usuarios ahora por **doc id = `request.auth.uid`** (antes usaba campo
  `uidAuth` que los docs no tienen), agregadas `reviews` (read público, create auth, delete si
  `authorId==auth.uid`) y `chats`/`messages` (solo participantes). Verificado: read público OK,
  chat sin auth 403, write anónimo 403.

**Gotchas de la API de Rules (para no repetir):** la API de releases NO soporta PUT/PATCH "update"
(PATCH rechaza el body con cualquier nombre de campo y PUT da 404/400). Actualizar = DELETE + POST
create. El DELETE con `%2F` escapado en el id `cloud.firestore/alkilappdb` NO borra nada (la ruta
debe ir con el slash literal en la URL).

**Pendientes (front, NO tocado en esta ronda):** la app Android aún no consume `chats`, `reviews`,
`features`, `location`, `photos` (URLs), ni los campos de perfil — el SEED y las reglas ya están;
faltan las pantallas (inbox de chat, reviews en perfil, detalles con features) en una ronda futura.

---

## Módulo CHAT + Destacado/Verificación (2026-09-15, Nativo Android)

El padre eligió **"Nativo Android (Recomendado)"** y **"Chat primero (Recomendado)"** para el diseño
(nada de React/Tailwind: se adapta todo a nuestro stack). La bandeja y la conversación quedaron
**implementadas y compiladas**, y el APK se instaló en el Xiaomi.

**Chat (bandeja + conversación):**
- `ModeloChat.kt` (datos: `ChatAlkil`/`Mensaje`/`PerfilUsuario`) — ChatAlkil.desde rellena nombre de
  la otra parte, inicial, `unreadMio`, `listingTitle`, `ultimo`; PerfilUsuario.buscar lee
  `usuarios/{uid}` (nombre = `name ?: nombre ?: email-prefix ?: uid.take(6)`, verificado =
  `verification.identityVerified` **o** `verificationBadge==true`).
- `ChatListActivity` (bandeja): listener `chats whereArrayContains("participants", miUid)`, orden
  por `lastMessageAt` desc, badges no-leído (coral), carga de perfiles una vez por uid/sesión.
- `ChatDetailActivity` (conversación): snapshot listener ordenado por `sentAt`, auto-scroll solo si
  estás al fondo, envía con `senderId+text+serverTimestamp` y actualiza el chat (`lastMessage`,
  `lastMessageAt`, `unreadCount.<otro> += 1`), marca leído `unreadCount.<mio>=0`, header con avatar/
  nombre/verificado/rating/listingTitle. Apertura auth-gated desde `btnChat` en el cardBusqueda.
- Layouts/recursos: `item_chat.xml`, `activity_chat_list.xml`, `item_mensaje.xml`,
  `activity_chat_detail.xml`; paleta rediseñada (azul `#1B72E8` + coral `#FF5A5F`, fondos suaves),
  drawables `ic_chat/ic_send/ic_back/bg_avatar/bg_burbuja_mia/bg_burbuja_otro/bg_badge_no_leido/
  bg_input_chat`. Actividades registradas en el Manifest (`windowSoftInputMode="adjustResize"`).
- **Seed actualizado**: `chat-papa-renzo` y `chat-papa-diego` incluyen el UID REAL del padre
  (`smpJfxQT0vUMaCMalJWUej1qPnw1`) como participante con `unreadCount` 1 → le aparecen en su bandeja
  al tocar "Chat". `chat-demo-01` sigue (sin el padre).
- **Rules v3** (ruleset `50640439-6f26-4c36-a5ac-460f18b2cf3d`, desplegado en los 2 releases):
  `chats` create valida con `request.resource.data.participants` (resource es null en create, la v2
  lo bloqueaba), ≥2 y ≤10 participantes, debe incluir `auth.uid`; `messages` require participante vía
  `get(...) is map` guard y `senderId==auth.uid` + texto 1..1000.

**Destacar Anuncios + Verificación de Confianza (misma sesión, pedido del padre):**
- Esquema (fusionado, verificado en seed): en `propiedades` → `isFeatured` (Boolean) y
  `featuredUntil` (Timestamp); en `usuarios` → `trustLevel` ("none"|"basic"|"verified_premium") y
  `verificationBadge` (Boolean). Seed: miraf-hab y casa-surco destacadas (7d y 3d); Renzo
  verified_premium/badge true, Diego basic/badge true, Valeria none/badge false.
- Tarjeta `item_propiedad.xml`/`PropiedadAdapter`: borde dorado sutil (`alkil_gold #C9922A`,
  stroke 2dp) + etiqueta flotante "⭐ Destacado" cuando `esDestacado` (= isFeatured && featuredUntil
  > ahora); chip "Verificado por Alkilapp" (escudo, fondo menta) cuando el propietario tiene
  `verificationBadge`; destacados se ordenan PRIMERO (antes que por cercanía).
- Escudo verificado nuevo `ic_verificado_escudo` (shield+check) usado en bandeja de chat, header de
  conversación y tarjeta de inmueble; en el header de la conversación se agrega la línea explicativa
  "Este propietario verificó su DNI y no tiene reportes negativos" (`verificado_explica` para la futura ficha).
- `MainActivity.cargarVerificacionPropietarios()`: tras el listener de `propiedades`, busca en
  `usuarios` el badge de cada `idPropietario` y lo pasa al adapter (sin N+1: festivos distinct).
- **Backend**: nuevo `backend/queries.js` con `obtenerPropiedadesActivasLima({detallado,limite})` —
  activas de Lima (ciudad=="Lima", estado=="disponible"), **destacadas primero** (isFeatured &&
  featuredUntil>ahora), luego normales por `createdAt` desc (orden en memoria: Firestore no ordena
  por booleano compuesto). `npm run query-demo` hace el demo. El esquema tolera índices futuros con
  un campo `featuredRank` si el catálogo crece.

**2 build/install exitosos hoy** (chat primero, luego +destacado/verificación). Errores corregidos en
el camino: `xmlns:app` faltante en las raíces de las 2 actividades (error de mergeDebugResources),
`update()` de Firestore requiere un `Map` (no Pair), `TextView` no tiene `textStyle` (usar
`typeface`). **Pendiente de prueba en vivo** (padre toca, agente lee): abrir bandeja con los 2 chats,
responder en conversación, ver borde/etiqueta dorada y chips verdes. Pendientes globales: push a
GitHub + release, iOS de AlkilApp.

---

## Ficha del inmueble + TAREA A/B de confianza (2026-09-15, también en vivo)

Pedido del padre: "al entrar a la propiedad el chat debe aparecer, y las imágenes deben mostrarse como una
galería"; luego definir las TAREA A (verificación por correo con código) y TAREA B (sistema de denuncias
con moderación). Todo implementado, compilado, instalado en el Xiaomi y **probado contra la BD real**.

**Ficha (`PropiedadDetalleActivity`, nueva):**
- `activity_propiedad_detalle.xml` (ScrollView): galería `ivPreview` + miniaturas `hsvThumbs`/`llThumbs`
  (repo-visual), título/dirección/precio, chips ambientes/superficie, comodidades, descripción, chips
  "Destacado" (dorado) y "Verificado" (escudo), botón coral **"Chatear con el Propietario"** y tarjeta
  del propietario (`cardPropietario`: avatar, nombre, escudo verificado, rating, contacto).
- Imágenes sin librería externa (build.gradle.kts sin Coil/Glide/ViewPager2): base64 `fotos` se
  decodifican con BitmapFactory (submuestreo 2); URLs (`photos`, picsum del seed) se descargan con
  `HttpURLConnection` en `Executors.newSingleThreadExecutor()` + `Handler(mainLooper)`. Preview =
  primera imagen; tocar miniatura la selecciona (alpha 1.0/0.6).
- `Propiedad.kt` ahora también parsea `photosUrl` (`photos` del doc). Se pasan por Intent (String
  ArrayLists). Si no hay ninguna foto → `tvSinFotos`.
- **Chat determinístico por inmueble**: botón crea si no existe `chats/inm-<listingId>` con
  `participants=[miUid, idPropietario]`, `listingTitle`, `unreadCount` inicial 0/0, `lastMessageAt`
  serverTimestamp — mismo id para ambos lados, sin índice compuesto. Oculta texto a "Este inmueble es
  tuyo" (sin acción) si soy el dueño; pide login si no hay sesión. Abre `ChatDetailActivity` (reusa
  EXTRA_CHAT_ID/EXTRA_LISTING/EXTRA_OTRO_UID de ChatListActivity).
- **Click de tarjeta**: adapter pasó de centrar el mapa a `abrirDetallePropiedad()` (todos los extras).
- **under_review oculto**: el listener de `MainActivity` filtra `estado != "under_review"` (además del
  `estado=="disponible"` de queries.js). La demo de moderación oculta el inmueble del listado.

**TAREA A — `backend/verificacion.js` (verificado en vivo, `npm run verificar-demo`):**
- `crypto.randomInt` 6 dígitos; doc `verifications/{email}` (id = correo normalizado) con `code`,
  `expiresAt` (10 min), `attempts` (máx 5). `validarCodigo()`: not_found/expired/invalid (suma
  intento) hasta el correcto → busca `usuarios where email==correo`, actualiza
  `verification.emailVerified=true`, `trustLevel="basic"`, `verificationBadge=true`,
  `verificacionAt`, y borra el código persistido. El map "users"→nuestra colección `usuarios` sigue
  la decisión de fusión. Usa `Timestamp.fromDate()` del SDK (ni objeto plano: corrompe el timestamp).
- Demo real: Valeria (`u-inquilino-1`, valeria.quispe@example.pe) quedó verificada basic.

**TAREA B — `backend/moderacion.js` (verificado en vivo, `npm run reportes-demo`):**
- `registrarDenuncia({listingId,reporterId,motivo,detalle})` → doc `reports` (createdAt server).
  `aplicarDenuncia(ref)` = transacción: `reportsCount + 1`; si llega a 3 y `estado=="disponible"` →
  `estado="under_review"`. `iniciarEscuchaDenuncias()` = listener onSnapshot ("added", idempotente
  por Set) replicando el trigger de Cloud Function del spec. `obtenerEstado()` para consultas.
- Demo real: `seed-l-depa-sanmiguel` pasó de disponible a **under_review** con 3 denuncias (luego
  re-seed lo restauró). En producción es un Cloud Function trigger, el listener es la versión local.

**Rules v4** (ruleset `d605869a-39bb-4f39-967c-f72a220d266f`, desplegado en los 2 releases tras
DELETE+POST — el PUT sigue dando 404): + `reports` (read auth; create con `reporterId==auth.uid` y
`listingId`/`motivo` string; update/delete solo del autor) y `verifications` (doc id = correo; solo
lectura/creación/borrado por `request.auth.email==correo`). Todo lo de v3 (chats/messages) intacto.

**Estado al cierre:** build OK e instalado en el Xiaomi; pendiente de prueba en vivo por el padre
(ficha con galería, chat al entrar a la propiedad, bandeja con `inm-<id>`). Re-seed hecho para dejar
los datos demo canónicos (5 activas: 2 destacadas). El resto: push a GitHub + release; iOS de AlkilApp.

---

## Correcciones en vivo (2026-09-15, ronda ficha): chat que no abría + denunciar + perfil del propietario

El padre reportó al probar en el Xiaomi: "el chat no abre desde la ventana del alquiler, no aparece la
opción para denunciar, debe permitir ver el perfil del arrendatario". Tres arreglos:

**1) Chat no abría — ROOT CAUSE de rules (no era bug de UI):** la ficha hacía
`ref.get()` sobre `chats/inm-<id>` (doc inexistente) para decidir si crear. La regla de read era
`auth.uid in resource.data.participants`, y en un doc inexistente `resource.data` es **null** →
`PERMISSION_DENIED`, el `onSuccessListener` nunca corría y el botón parecía muerto (sin el
`addOnFailureListener` ni siquiera había toast). Doble corrección:
  - **Rules v5** (ruleset `a220c618-94cb-47eb-ab5f-f0ec46a1eeb3`, desplegado en los 2 releases): el
    read de `chats` ahora acepta el patrón get-or-create:
    `allow read: if request.auth != null && (auth.uid in resource.data.participants ||
    !exists(/databases/$(database)/documents/chats/$(chat)))`.
  - La app ya NO lee antes: `abrirChatPropietario()`/`PerfilPropietarioActivity.abrirChat()` hacen
    `set(merge:true)` del chat determinístico (participants/listingId/listingTitle, SIN unreadCount/
    lastMessage para no pisar un chat existente) y abren la conversación directo. Error → toast
    con `e.localizedMessage` (nunca más fallo silencioso).

**2) Denunciar anuncio (colección `reports` desde la app):** botón "Denunciar anuncio" (outlined
rojo `alkil_rojo #D32F2F`, icono `ic_flag`) debajo del botón de chat, oculto si el inmueble es mío.
`Dialog` (`dialog_denuncia.xml`): Spinner `spDenunciaMotivo` (array `motivos_denuncia`:
"no coincide con las fotos"/"información falsa"/"precio engañoso"/"presunta estafa"/"contenido
inapropiado"/"otro") + EditText detalle opcional. Envía `reports/{auto}` con `listingId`,
`reporterId=auth.uid`, `motivo`, `detalle`, `chapter:'reports"`, `createdAt` serverTimestamp.
Las rules v4/v5 ya permiten create con `reporterId==auth.uid`; el conteo/under_review lo hace el
backend (`moderacion.js` / Cloud Function). Requiere sesión (toast si no).

**3) Perfil del propietario:** nueva `PerfilPropietarioActivity` (`activity_perfil_propietario.xml`,
registrada en el Manifest). Al tocar la tarjeta del propietario en la ficha (ahora con "› Ver perfil"
+ `ic_chevron`) abre: avatar (foto `profilePicture` por URL con executor+Handler; fallback inicial
al estilo chat), nombre, chip de rol (propietario/inquilino/ambos, mapea también dueño/arrendador/
arrendatario), escudo "Verificado por AlkilApp" + nivel de confianza (trustLevel
none/basic/verified_premium), rating con `totalRatings`, "En AlkilApp desde {fecha}", teléfono y
correo, y **reseñas** en vivo (`usuarios/{uid}/reviews` orderBy createdAt desc, estrellas llenas/
huecas con `ic_star` tintado dorado/gris, autor + comentario). Botón inferior "Chatear" que reusa el
mismo chat determinístico `inm-<listingId>` (se oculta si es mi perfil o no viene desde un inmueble).

**Estado al cierre:** compiló + instaló en el Xiaomi (hubo un "device offline" pasajero de adb, se
reinició con `adb kill-server` y volvió como `6phyeanrfyhmozv8 device`). Pendiente de validar en vivo:
abrir ficha → (a) "Chatear con el Propietario" abre la conversación, (b) "Denunciar anuncio" guarda
en reports (el padre toca, el agente lee con uiautomator / consulta por SDK), (c) tocar la tarjeta →
perfil con reseñas de Renzo (2) y botón chatear. Pendientes globales siguen: push a GitHub + release,
iOS de AlkilApp.

---

## Ronda correcciones (2026-09-15): filtro distrito mueve el mapa + botón mi ubicación + alta como formulario + tipo "habitación"

Pedido del padre: "la filtracion por distritos no te mueve o ubica en la zona referenciada, por otro
lado debes agregar el boton de mi ubicacion, para que retorne a donde estas ubicado, ademas el
registro de nueva propiedad debe ser en un formulario y no en pop-up, debes agregar en la lista de
opciones de alquiler a habitacion". TODO implementado, compilado e instalado en el Xiaomi.

**1) Filtro distrito → mueve el mapa a la zona:** `MainActivity.actualizarZonaMapa()` (llamada al
aplicar/limpiar filtros): si hay filtro activo, marca los inmuebles visibles
(`PropiedadAdapter.visibles()`, nuevo método público que devuelve la lista ya filtrada) y anima la
cámara con `LatLngBounds` (padding 90, zoom 16 si es uno solo); si el filtro no trae resultados →
toast `filtros_sin_resultados` y vuelve a la ubicación del usuario. Al limpiar filtros → el mapa
vuelve a `ultimaUbicacion` (marcador "Tu Ubicación"). Antes el mapa nunca se movía al filtrar.

**2) Botón "mi ubicación":** nuevo `fabMiUbicacion` (FAB `layout_gravity="bottom|end"`, margen bottom
120dp para quedar sobre el bottomSheet, icono `@android:drawable/ic_menu_mylocation`). Click →
`irAMiUbicacion()`: si falta permiso lo pide (mismo requestCode), si el mapa está listo llama a
`obtenerUbicacionActual()` (ubicación fresca `getCurrentLocation` → centrar + marcador + `setUbicacion`
para reordenar la lista por cercanía).

**3) Alta como formulario (adiós pop-up):** nueva `RegistrarPropiedadActivity` (registrada en el
Manifest con `windowSoftInputMode="adjustResize"`) + `activity_registrar_propiedad.xml`: cabecera con
back + título (estilo ficha), ScrollView con los mismos campos que el diálogo pero en
`TextInputLayout`/`TextInputEditText` (floating labels): título, precio, dirección, descripción, y
spinners tipo/operación/moneda/distrito, selector de fotos (máx 3, thumbnails tappables), botón coral
"Guardar" full-width. La lógica se MOVIÓ ahí: `fotosLauncher`/`comprimirFoto()`/`renderizarPreviews
Fotos()`/`guardarPropiedad()`. **Ubicación:** ya no usa el centro del mapa del pop-up — MainActivity
pasa `EXTRA_LAT/EXTRA_LNG` = `ultimaUbicacion ?: cameraPosition.target ?: (0,0)`, y la actividad
refresca posición fresca con FusedLocation si hay permiso (`actualizarUbicacionSiPosible()`). Se
eliminaron de MainActivity todo el código del pop-up y `dialog_propiedad.xml` (borrado) +
`mostrarPropiedadEnMapa` (era código muerto desde que la tarjeta abre la ficha). String
`prop_ubicacion_info` ahora dice "La ubicación se toma de tu posición actual". En MainActivity se
protegió el acceso a `mMap` con `::mMap.isInitialized` (FAB si el mapa aún no cargó).

**4) "habitacion" en `tipos_inmueble`:** item agregado (departamento, casa, **habitacion**, cochera,
deposito, local, oficina). Sólo afecta el spinner del formulario; backend/seed guardan `tipo` sin
validar contra lista, y el seed ya tenía una habitación co-living (`seed-l-hab-miraflores`).

**Constantes/recursos nuevos:** `fabMiUbicacion`, `mi_ubicacion_btn`, `filtros_sin_resultados`,
`activity_registrar_propiedad.xml`, `RegistrarPropiedadActivity`. El build avisó de deprecaciones
`GoogleSignIn` (preexistentes, ok) y de `compileSdk 36` vs AGP (preexistente, ok). APK instalado
(`adb install -r` OK en `6phyeanrfyhmozv8 device`).

**Estado al cierre:** build OK + instalado. **Pendiente de validación en vivo por el padre:** (a)
aplicar filtro "San Miguel"/"Miraflores" y que el mapa se mueva a esa zona con marcadores; (b)
tocar el FAB de ubicación y volver a donde está parado; (c) el "+" abre el formulario fullscreen y
guarda con la ubicación correcta; (d) la opción "habitacion" aparece en el spinner Tipo. Pendientes
globales siguen: push a GitHub + release; iOS de AlkilApp.
---

**Ronda visor de fotos + "Mi perfil" (2026-09-15, en vivo):** - **Visor de fotos con zoom a pantalla completa:** tocar la foto principal (o miniatura) de la ficha abre `FotoZoomActivity` (fondo negro, ViewPager2, indicador "Foto i de n", bot�n X). Zoom real: nuevo `ui/ZoomImageView.kt` (custom view, sin librer�as: Matrix + ScaleGestureDetector pinch/pan + GestureDetector doble toque 3x, l�mites `limitarTraslacion()`, en escala 1 no consume gestos para que el pager haga swipe). Carga en resoluci�n COMPLETA (base64 con decodificaci�n sin inSampleSize v�a prefijo `b64:`; URLs con HttpURLConnection+Executor/Handler). La galer�a de la ficha ahora guarda `fuentes` (base64/URL en orden) + `indiceActual`; el toque de preview abre el visor en esa posici�n. **CRASH en vivo (padre probando) ? fix:** "Pages must fill the whole ViewPager2" porque el `ZoomImageView` se creaba sin LayoutParams; hace falta `RecyclerView.LayoutParams(MATCH_PARENT, MATCH_PARENT)` expl�cito (el constructor `(int,int)` de `ViewPager2.LayoutParams` es package-private, no usar ese). Dep nueva `androidx.viewpager2:viewpager2:1.1.0`.
- **Cabecera m�s intuitiva + "Mi perfil":** bot�n de sesi�n (`btnAuth`) ahora muestra "Mi perfil" cuando hay sesi�n (antes el correo) e "Ingresar" sin sesi�n. Con sesi�n, tocarlo abre di�logo con opciones **Mi perfil / Cerrar sesi�n**. Nueva `MiPerfilActivity` (formulario: foto circular ShapeableImageView estilo `AlkilAppCircle` (parent=""), nombre, tel�fono, correo readonly, spinner tipo de cuenta Propietario(a)/Inquilino(a)/Publico y busco ? `tipos_usuario` array; valores dueno/inquilino/ambos; foto base64 500px q70; guardado con `set(merge:true)` en `usuarios/{uid}`). **`guardarUsuarioEnBase()` ya no pisa el perfil**: lee el doc y con merge solo garantiza nombre/email/uidAuth/activo (no sobrescribe nombre editado; si el doc no existe crea con telefono/tipoUsuario dueno/fechaRegistro). `PerfilPropietarioActivity` muestra la foto con fallback base64 `fotoBase64` tras `profilePicture`.
- Validado: build OK, `adb install -r` OK, arranque sin FATAL, cabecera confirmada por uiautomator ("Mi perfil" en `btnAuth`; lista con "Habitaci�n en departamento compartido (Co-living Miraflores)" Destacado). FotoZoom/MiPerfil no exportadas ? el shell no puede arrancarlas (correcto). **En vivo:** el padre toc� una ficha ? foto ? CRASH ViewPager2, se corrigi� y se re-instal�; faltan revalidar zoom/pinch/doble-tap y el formulario Mi perfil completo en el tel�fono.

**Ronda correcciones (misma sesi�n):** - **"Mi perfil" abr�a un pop-up con el correo** (di�logo con opciones) ? ahora el bot�n de cabecera (sesi�n iniciada) abre DIRECTAMENTE MiPerfilActivity (onBotonAuth sin di�logo); "Cerrar sesi�n" se movi� al final del formulario de perfil (tnPerfilCerrarSesion, texto rojo) ? uth.signOut() + inish() (el header se refresca v�a onStart()?ctualizarUiSesion()). Sin sesi�n sigue abriendo el di�logo de autenticaci�n.
- **Formulario de alta con los campos principales** del ejemplo seed (Co-living Miraflores): se agregaron a ctivity_registrar_propiedad.xml + RegistrarPropiedadActivity � etPropAmbientes (habitaciones, inputType number ? mbientes Long), etPropSuperficie ("�rea (m�)", numberDecimal ? superficieM2 Double) y cgPropComodidades (ChipGroup con chips checkable desde el nuevo array comodidades_disponibles = Wifi/Agua/Luz/Ascensor/Balc�n/Terraza/Cocina equipada/Amoblado/Parqueo/Jard�n ? comodidades List<String>). Se guardan en el doc y ya la ficha los muestra.
- Build OK, instalado, arranque sin FATAL. Pendientes globales: push a GitHub + release; iOS de AlkilApp.

**Ronda autocomplete de direcciones (2026-09-15, pedido del padre: "cuando el usuario registre la direccion, debe permitir vincular con la busqueda de google maps y sugerir la direccion"):**
- **GCP ya estaba listo**: la clave "AlkilApp Android" (AIzaSyCidZ9XXN69qaqa_TdLhv1YTLYE-1AWsCs, en local.properties como MAPS_API_KEY) tiene places-backend.googleapis.com habilitado en piTargets + restricci�n Android com.alkilapp/SHA-1 b4ed9dc4 ? el autocomplete funciona SIN tocar consola. (Encontr� de paso la clave "Clave de API Alkilapp" 5d762dc... con androidKeyRestrictions vac�o + solo maps/places.)
- **Dependency a�adida**: com.google.android.libraries.places:places:3.5.0 (pp/build.gradle.kts).
- **App.kt nueva**: Application que lee la clave del meta-data del Manifest (com.google.android.geo.API_KEY) y llama Places.initialize(); registrado como ndroid:name=".App" en el Manifest.
- **RegistrarPropiedadActivity**: campo etPropBuscarDir ("Buscar direcci�n en Google Maps", hint ej. Av. Jos� Larco 1234, Miraflores) con debounce 450ms, min 3 letras ? indAutocompletePredictions (query + setCountries(["PE"]) + setLocationRestriction(RectangularBounds.newInstance(rectPeru))) ? sugerencias como TextViews clicables en llSugerencias (LinearLayout din�mico; el SDK NUEVO usa LatLngBounds SOLO v�a LocationRestriction/LocationBias, no directo: el builder espera com.google.android.libraries.places.api.model.LocationRestriction; setLocationBias espera un punto LatLng - el error de compilaci�n "inferred type is LatLngBounds but LocationBias? was expected" me llev� a javap del jar). Al tocar una sugerencia ? etchPlace(placeId, [ADDRESS, LAT_LNG]) ? rellena etPropDireccion con la direcci�n completa + sobrescribe latAgregar/lngAgregar con las coordenadas del lugar (m�s precisas que la ubicaci�n actual) y el texto 	vPropUbicacionInfo muestra "Ubicaci�n del punto elegido: lat, lng (GPS)". Protecci�n anti-stale: token consultaBusquedaActual.
- Build OK, instalado, sin FATAL. Pendiente probar en vivo (el padre) y confirmar que la clave responde en su Xiaomi.
- Pendientes globales: push a GitHub + release; iOS de AlkilApp.

## Ronda 25 (2026-09-20, instalado en Xiaomi HGR4RJCK): registro nacional + share + chat fix

**Registro a nivel nacional (25 departamentos)** — `strings.xml` agrega array `departamentos_peru` con 25 departamentos. `RegistrarPropiedadActivity`: nuevo spinner `spPropDepartamento` + listener que actualiza `spPropDistrito` dinámicamente (Lima → 43 distritos; otros → "Sin distrito"/"Otro"). `MainActivity` filtros: mismo patrón en `abrirDialogoFiltros()`. String `filtros_scope_info` actualizado a "Ahora se filtra a nivel nacional (Perú)...".

**Botón compartir en ficha** — `PropiedadDetalleActivity` agrega `btnDetalleCompartir` en cabecera (icono share nativo) → genera link `https://alkilapp.com/propiedad/{propId}` → `Intent.ACTION_SEND` con share sheet del sistema.

**Fix permission denied en chat** — `participants` array ordenado (`.sorted()`) en `PropiedadDetalleActivity.abrirChatPropietario()` y `PerfilPropietarioActivity.abrirChat()`. Evita `PERMISSION_DENIED` en regla Firestore `update` cuando chat ya existe con orden distinto.

**Técnico**: version bump v1.39 (vc21), commit `c4f29e7`, release GitHub `v1.39`, APK instalado en Xiaomi (serial HGR4RJCK).

## Ronda 26 (2026-09-20): ciudades por departamento (25 deptos)

**Ciudades por departamento (25 deptos)** — `strings.xml` agrega 25 arrays `ciudades_<departamento>` con capitales y ciudades principales. `RegistrarPropiedadActivity` y `MainActivity` (filtros): spinner Departamento con listener que actualiza spinner Ciudad dinámicamente (Lima → 43 distritos; otros → capitales + ciudades principales). String `filtros_scope_info` actualizado a "Ahora se filtra a nivel nacional (Perú)...".

**Fix chat** — revertido `.sorted()` en array `participants` (PropiedadDetalleActivity + PerfilPropietarioActivity) → mantiene orden original al crear chat, evita `PERMISSION_DENIED`.

**Técnico**: version bump v1.40 (vc22), commit `beb9d85`, release GitHub `v1.40`.

## Ronda 24 (2026-09-19): registro celular + perfil solo-lectura + verif 2 fotos + admin filtros

**Pedido del padre (4 cambios):**
1. **Registro con celular obligatorio** — `AuthActivity`: campo `Celular` (solo modo registro, validación ≥9 dígitos), `guardarUsuarioEnBase(nombre, telefono)` escribe `telefono` en `usuarios/{uid}`. `activity_auth.xml` nuevo `tilAuthTelefono` visible en modo registro.
2. **Mi Perfil: nombre y correo no editables** (datos únicos) — `activity_mi_perfil.xml` reemplaza `etPerfilNombre` por label + `tvPerfilNombre` (solo lectura, igual que email); nota `perfil_datos_fijos`; `MiPerfilActivity.guardarPerfil()` elimina `nombre` del mapa, solo guarda `telefono` + `tipoUsuario`.
3. **Verificación identidad: 2 fotos (frente + adverso)** — `activity_verificacion.xml` + `VerificacionActivity` con dos launchers (`fotoLauncher` + `fotoReversoLauncher`); valida ambos; escribe `imagen` + `imagenReverso` en `verificaciones/{uid}`. Reglas `create` exigen ambos campos (≤900KB cada uno).
4. **Admin panel: filtros + enlaces resumen** — `backend/admin/server.js`: `vistaVerificaciones` (filtro estado), `vistaPublicaciones` (filtro estado + destacado), `vistaReportes` (filtro estado), `vistaUsuarios` (filtro tipo/verificación/trustLevel); `vistaResumen` tarjetas como `<a class="stat-link">` clicables. Sesiones stateless: cookie `alkil_admin=payload|ts.signature` (HMAC-SHA256, TTL 24h), sin `sesiones` Set en memoria.

**Técnico:**
- Firestore rules v9 (`e8b5deb4-c1fd-45db-ad93-1d3e4909036c`): `verificaciones` create exige `imagen` + `imagenReverso`.
- Version bump: `versionCode 20`, `versionName 1.38` (v1.38 / vc20).
- Build: `gradlew assembleDebug` OK, APK instalado en Xiaomi 14 (serial 6phyeanrfyhmozv8).
- Admin panel redeployed Cloud Run rev `alkilapp-admin-00012-w7g` (URL `https://alkilapp-admin-288429280561.us-central1.run.app`).
- Commit `6ca45ba`, tag `v1.38`, release GitHub `v1.38`.

## Ronda 20 (2026-09-19 madrugada): 10 inmuebles ficticios en distintos distritos de Lima
Pedido del padre: "carga a la base de datos 10 inmuebles mas para ver como se visualizará, con direcciones ficticias en diferentes distritos de lima".
- **Seed ejecutado** (`node seed.js` en `D:\alkilapp\backend`, SA `credentials/alkilapp-seed-sa.json`, base `alkilappdb`): ahora hay **14 docs `esDemo=true`** en `propiedades` (4 viejos + 10 nuevos). Los 10 nuevos: San Isidro, Barranco, La Molina, San Borja, Magdalena del Mar, Jesús María, Lince, Pueblo Libre, Los Olivos y Ate; 7 deptos + 2 habs + 1 casa + 1 loft ("otro"); todos `estado="disponible"` con direcciones ficticias (Av. Petit Thouars, Jr. Unión, Calle Los Girasoles, Av. San Borja Norte, Av. Brasil, Av. Cuba, Av. Arenales, Av. Bolívar, Av. Alfredo Mendiola, Av. Separadora Industrial).
- **3 destacados nuevos** (`isFeatured + featuredUntil`): casa-lamolina (7d, S/4200) y losollivos (15d, S/1100); más los 2 viejos (miraflores, casa-surco) → el orden tier del feed (destacado → verificado → sin verificar → cercanía) se puede visualizar.
- **Nuevo propietario seed `u-propietario-2`** (María Gutiérrez, `trustLevel:'none'`, `verificationBadge:false`, NO verificado) con 3 de los inmuebles (barranco, jesusmaria, lince) → aporta el tercer tier "sin verificar" a la visualización. Se agregó al loop de limpieza de seed y a `users`.
- **FOTOS GOTCHA confirmado:** la TARJETA del listado (`PropiedadAdapter`) lee SOLO `item.fotos` (base64); las URLs `photos` NO se muestran en el card (si `fotos` está vacío → `ivFoto.visibility=GONE`, tarjeta sin imagen). Por eso los 4 seeds viejos salen sin foto. Fix para los 10 nuevos: helper `solidPngBase64(hex)` en seed.js genera un **PNG sólido 4x4 base64 sin dependencias** (signature + IHDR + IDAT con `zlib.deflateSync` + IEND, CRC32 manual) y `buildListing` acepta `fotoColor` → cada inmueble nuevo tiene `fotos:[<b64>]` de un color pastel distinto (`#C8D6E8`, `#E8D3C8`, `#D4C8E8`, `#C8E8D4`, `#E8E4C8`, `#D9C7B8`, `#B8C4CE`, `#C2C9D6`, `#E8CFC8`, `#DCD6C9`) → el card muestra área de foto.
- **App NO requiere reinstall / bump** (cambio 100% de base; el feed es snapshot de toda `propiedades` → los 10 aparecen en vivo al abrir). Commit `35e17e3` pusheado. Sin tag (no cambió código Android).
- Verificación: query Firestore de `propiedades where esDemo==true` = 14 docs, `fotos` con cabecera PNG válida. En el teléfono el padre estaba en la ficha de su publicación real ("Departamento 2 cuartos", Los Olivos) cuando el agente intentó verificar → **no interrumpir**: pendiente que el padre confirme la visualización (mapa/listado).
- Gotcha adb de la sesión: `adb kill-server` del shell PowerShell mata el proceso del propio tool (ChildProcess.kill) → usar `adb reconnect offline` (funciona si el dispositivo quedó en "offline").

## Ronda 21 (2026-09-19): Firma + foto en menú + badge chats + logo launcher
- **Firma**: `nav_footer` = "AlkilApp by Jh4ckT" (pie del menú hamburguesa) + nuevo `auth_firma` bajo el texto legal del login. Verificado en vivo (`AlkilApp by Jh4ckT @ [0,2147]`).
- **Foto de perfil en el header del menú**: `panel_menu.xml` → `ivNavFoto` (ShapeableImageView circular `AlkilAppCircle`, scaleType centerCrop) sobre `tvNavAvatar` (iniciales, GONE con foto). MainActivity `cargarFotoNav()`: get `usuarios/{uid}` → `fotoBase64` (NO_WRAP + BitmapFactory en executor) o `profilePicture` (HttpURLConnection), postea a `handlerMain`. En vivo: `tvNavAvatar` GONE + `ivNavFoto` visible con la foto del padre.
- **Badge de chats sin leer**: `object ChatNoLeidos` (total @Volatile, `actualizar/suscribir/desuscribir` con CopyOnWriteArrayList) en ChatNotificaciones.kt; `MonitorChats` lo alimenta en CADA snapshot (`chats.sumOf{it.unreadMio}`, 0 al desconectar). MainActivity: suscripción en onCreate (callback→runOnUiThread `actualizarBadgeChats`), refresco en `onStart`/`actualizarUiSesion`, desuscribe en onDestroy; `tvNavChatBadge` (bg_badge_no_leido) en fila Mis chats, GONE en 0 (verificado en vivo). Las notificaciones locales de la Ronda 7 siguen operando; FCM/push real pendiente-opcional.
- **Logo launcher** (ver Ronda 22 para el rediseño final con caché del launcher aclarada).

## Ronda 22 (2026-09-19): logo rediseñado (techo+chimenea separados)
El padre reportó que el logo "no se veía bien" (era el pin-casa fusionado de la Ronda 21). **Rediseño en 3 `<path>` independientes** en `ic_launcher_foreground.xml` (NO evenOdd compartido — puntearía): (1) chimenea con cap: `M56,14 L68,14 L68,22 L56,22 Z M60,22 L64,22 L64,40 L60,40 Z`; (2) techo trapezoidal: `M54,30 L82,52 L82,58 L26,58 L26,52 Z`; (3) pin teardrop con ojo de cerradura (evenOdd local, circle r5 + ranura): tip en (54,90), body `M54,64 C62,64 ... Q40,90 54,90 Z`. Todo coral `#FF6B5E` sobre navy `#0B2447`.
**Trampa launcher MiUI**: el pixel-check de la Ronda 21 daba esos celulares lavados por caché del launcher; tras `install -r` + `am force-stop com.miui.home` el icono renderiza (pixel-scan: navy~1763 + coral~841 con #FF6B5E exacto por celda, 2 celdas; distribución vertical techo=728-775, gap, pin=800-847). Build+instalado, v1.35 (vc17), commit `a83d8f4`, release https://github.com/jh4ckt/alkilapp/releases/tag/v1.35.

## Ronda 22b (2026-09-19): logo → revert al original solo con paleta
El padre siguió sin agradar el pin-casa: "retorna al logo previo y solo cambia la paleta a coral y azul marino". `ic_launcher_foreground.xml` vuelve al path ORIGINAL de la casa (`M54,32 L30,56 L34,56 L34,76 L74,76 L74,56 L78,56 Z M44,76 L44,62 L64,62 L64,76 Z`) con `fillColor=@color/alkil_coral #FF6B5E`; background navy `#0B2447` (ya estaba). Pixel-check tras force-stop del launcher: navy=4581, coral=1320, cluster vertical único 722..818 = casa. v1.36 (vc18), release `v1.36`.

## Ronda 23 (2026-09-19): chats cerrados para inmuebles finalizados (reglas v8)
Pedido del padre: "si una publicacion fue finalizada o alquilada, cualquier chat relacionado a la misma debe ser cerrada para el usuario solicitante". **App** (`ChatDetailActivity`): `listingId` = `EXTRA_LISTING_ID` (nuevo en `ChatListActivity`, se pasa al abrir) con fallback `chatId.removePrefix("inm-")`; `vigilarEstadoInmueble()` hace snapshot de `propiedades/{listingId}` y si `estado=="finalizado"` → banner `cardChatCerrado` (string `chat_cerrado_inmueble`, drawable nuevo `ic_lock.xml` candado material) + `etEntrada.isEnabled=false` + `btnEnviar.isEnabled=false` + guard en `enviarMensaje()`. **Reglas v8** (`backend/firestore.rules`, deploy reglas.js → ruleset `5c110653…`): función `inmuebleCerrado()` en `match /chats/{chat}/messages/{message}` que lee el chat (get), su `listingId`, la propiedad, y bloquea `create` si `estado=='finalizado'`. **GOTCHA**: `.lower()` NO existe en Cloud Firestore Security Rules → INVALID_ARGUMENT en el règleset POST; comparar el string directo. Otras notas: las funciones con `let`+`if` pueden romper el parseo; usé `let c = get(...)` + expresión única `c.exists && c.data.listingId is string && ...`. Verificado en vivo (SA marca `seed-l-hab-miraflores` finalizado → abrí `chat-papa-renzo` → banner `[136,1955][1018,2037]` + EditText enabled=false; revertido). v1.37 (vc19), commit `fcaf080`, release `v1.37`.

## Ronda 16 (2026-09-18): Fix listado invisible + selector de ubicacion en el mapa

**Sintoma reportado por el padre (tras instalar v1.29):** "no me aparece el listado" - el bottom sheet
(la ficha de listado sobre el mapa) no se veia, el mapa quedaba a pantalla completa.

**Diagnostico (pixeles como verdad, NO dumpsys):** el dumps arrojo que el sheet tenia bounds
`0,1515-800,2230` en una ventana de 800x1226: el panel estaba COMPLETAMENTE fuera de pantalla (por
debajo del viewport), no oculto ni transparente. Ademas se descubrio una trampa de debug: en este
telefono corre OTRA app con su propio `.MainActivity`/hierarchy en background (la app de stickmans
del proyecto hermano) y `dumpsys activity top` mezcla sus view hierarchies - SIEMPRE confirmar la app
enfocada con `dumpsys window | findstr mCurrentFocus` y usar muestreo de pixeles como fuente de verdad.

**Doble causa probable y doble fix (ambos en v1.30):**
1. **Estados guardados que sobreviven `install -r`:** BottomSheetBehavior guarda su estado/offset en
   el SavedState de la Activity; al reinstalar con `install -r` (como hace el padre) y restaurar la
   Activity, el sheet puede reaparecer con un offset antiguo que lo deja fuera de pantalla. Fix codigo:
   en `configurarBottomSheet()` tras `behavior.state = STATE_COLLAPSED` se agrego `sheet.post { state =
   STATE_COLLAPSED }` para REAPLICAR el estado en el primer layout y recolocar el sheet en el peek.
   Verificado: tras `uninstall`+`install` limpio el sheet renderiza; el `post {}` cubre el caso
   `install -r` del padre.
2. **Combinacion fragile fitToContents+maxHeight:** se deshizo el cambio de altura fija de Ronda 15.
   Ahora `behavior.isFitToContents = false` + `wrap_content` + `peekHeight 100dp` (el path de render
   probado en vivo en v1.28) y el tope del 60% se logra limitando la altura del RecyclerView:
   `binding.rvDepartamentos.layoutParams.height = (0.6*altoPantalla) - 76.dp` (76dp = handle+titulo).

Resultado: mapa hasta ~y1050, banda clara (sheet superficie) de ~y1050 al borde inferior con tarjetas.
Nuevo build + install verificado por pixeles antes y despues del hardening.

**Selector de ubicacion en el mapa (al publicar).** Nueva `MapaSeleccionActivity`
(`activity_mapa_seleccion.xml`): mapa fullscreen con cabecera (back + titulo "Elegir ubicacion"),
instruccion "Toca el mapa para colocar el marcador", boton "Confirmar ubicacion" (coral) y FAB "Mi
ubicacion" (`FusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY)`). Tap del mapa
coloca/mueve un marcador; si ya habia lat/lng (modo edicion) abre centrado con el marcador puesto,
si no centra en Lima. Confirmar devuelve `RESULT_OK` con `EXTRA_LAT_RESULTADO`/`EXTRA_LNG_RESULTADO`.
En `activity_registrar_propiedad.xml` nuevo boton outlined "Elegir ubicacion en el mapa" (icono
`ic_location`) justo bajo el campo de direccion. `RegistrarPropiedadActivity`: `mapaSeleccionLauncher`
(ActivityResultLauncher) -> setea `latAgregar/lngAgregar`, actualiza `tvPropUbicacionInfo` con
"Ubicacion elegida en el mapa: lat, lng" y `rellenarDireccionDesdeMapa()` (Geocoder en Thread +
runOnUiThread, solo si el campo direccion esta vacio). Activity registrada en el manifest
(`exported=false`). Strings nuevos SIN acentos (`prop_elegir_mapa`, `mapa_seleccion_*`).
Smoke test: la actividad arranca sin FATAL (verificada con manifest temporalmente exportado), pero el
tap-through NO se puede automatizar (INJECT_EVENTS bloqueado) -> pendiente prueba visual del padre.

**Debug helper aprendido esta ronda:** tras `uninstall`/`install` limpio la app muestra el dialog
"Activar ubicacion" (pref `location_permission_shown` reseteado) que bloquea la verificacion visual;
se puede pre-setear el pref sin tocar la UI via `adb shell run-as com.alkilapp sh -c 'echo <b64> |
base64 -d > shared_prefs/alkilapp_prefs.xml'` (heredoc directo falla por parsing del shell).

Android **v1.30 (vc12)**, commit `2354ac1`, tag `v1.30`, release
https://github.com/jh4ckt/alkilapp/releases/tag/v1.30. APK instalada en el Xiaomi. Pendiente prueba en
vivo del padre (sheet + selector de mapa + todo lo previo sin probar: v1.24-v1.29).

---

## Ronda 17 (2026-09-18): Bottom sheet MANUAL (adiós BottomSheetBehavior)

El padre reportó otra vez "no aparece el listado": **en la resolución nueva del Xiaomi (1080x2400,
density 450, scale 2.8125x; antes 800x1280/density 1.5 — el padre lo llama "nuevo dispositivo" porque
la app estaba desinstalada y los ajustes quedaron reseteados)** el sheet seguía sin verificarse aunque
v1.30 ya lo arreglaba a la resolución vieja. Se abandonó la librería por completo: **BottomSheetBehavior
DELETED** (su saved-state sobrevivía `install -r` y su offset roto volvía a esconder el sheet).

Nueva implementación 100% manual en `MainActivity.kt` (`configurarBottomSheet()`):
- El sheet es un `LinearLayout` con `layout_gravity="bottom"` SIN `layout_behavior`, con **altura fija =
  60% de la pantalla** (`displayMetrics.heightPixels * 0.6f`) puesta en código.
- **Minimizar = `sheet.translationY = alturaExpandida - 100.dp`** (asoma solo handle + título); expandir
  = `translationY = 0`. Sin behaviors, sin estados, sin offsets que restaurar.
- **Arrastre** del sheet por la cabecera (`sheetHeader` con OnTouchListener: DOWN/MOVE/UP con
  `rawY`, clamp a `[0, maxOffset]`); **tap** en la cabecera alterna expandido/minimizado (umbral 0.4x
  maxOffset para decidir lado al soltar y para el tap).
- `animarA(target)` con `ViewPropertyAnimator` (220ms, `setUpdateListener` actualiza FAB + padding del
  mapa en vivo); `actualizarOverlays(p)` = mismo cálculo que el viejo onSlide (bottomMargin del FAB y
  `mMap.setPadding`).
- Estado inicial COLAPSADO: `sheet.translationY = maxOffset` + `post{}` re-aplicado (sin animación).
- **Byte del peek**: `sheetHeader` y `rvDepartamentos` ahora con `background="@android:color/white"`
  sólido (antes `?attr/selectableItemBackground` transparente → se veía el mapa a través del strip del
  peek). El RecyclerView usa `layout_height="0dp" + layout_weight="1"` para llenar bajo la cabecera.

Verificación en vivo (1080x2400, uiautomator bounds): `bottomSheet` = `[0,2119][1080,2400]` (2119 =
2400-281 = peek 100dp exacto), `sheetHeader` = `[0,2119][1080,2299]` con "Inmuebles Cercanos" en
`[0,2153][467,2299]`, `rvDepartamentos` = `[0,2299][1080,2400]` con tarjetas reales; píxeles del strip
del peek ahora BLANCOS sólidos (antes coral = mapa a través del header transparente); sin FATAL.
Gotcha de debug de la sesión: `adb shell findstr` NO existe en el dispositivo (vierte error y rompe el
pipe) — filtrar logcat/host-side con Select-String; y `logcat -d` entero funciona para grep de FATAL.
(NOTA de antes sigue: `dumpsys activity top` mezcla la app de stickmans que corre en este teléfono —
confirmar con uiautomator/bounds.)

Android **v1.31 (vc13)**, commit `700d6c1`, tag `v1.31`, release
https://github.com/jh4ckt/alkilapp/releases/tag/v1.31. APK instalada en el Xiaomi. Pendiente: prueba
interactiva del padre (arrastrar/tocar cabecera del sheet, expandir a 60% y minimizar) + tap-through del
selector de ubicación en el mapa al publicar (v1.30, sin probar por nadie aún).

---

## Ronda 18 (2026-09-18): Marcadores de inmuebles en color oscuro

El padre: "el marcador de inmuebles, que no aparezca en rojo, cambialo por un color oscuro". Antes
`iconoDefault = BitmapDescriptorFactory.defaultMarker(HUE_RED)`; seleccionado seguía HUE_GREEN.

Nuevo pin personalizado `res/drawable/ic_marker_inmueble.xml` (38x50dp, teardrop `#1E293B` = slate-800/
`brand_primary` con centro blanco, consistente con el design system). **Gotcha CRÍTICO:** el primer
intento usó `BitmapDescriptorFactory.fromResource(R.drawable.ic_marker_inmueble)` y la app CRASHÓ en
arranque con `Failed to decode image. The provided image must be a Bitmap` (el dinámico de Maps no
decodifica XML/vectores con fromResource en este dispositivo). Fix: `cargarIconoRes()` en MainActivity
rasteriza el vector a `Bitmap` (AppCompatResources.getDrawable → `Canvas.draw` → un interior
`Bitmap.createBitmap`) y luego `BitmapDescriptorFactory.fromBitmap(bitmap)` — patrón estándar y seguro.
La app arrancó sin crash (verificado: mCurrentFocus = MainActivity, sin FATAL nuevo; además el padre
estaba tocando el teléfono en ese momento — abrió la ficha de un inmueble desde el mapa, prueba real en
vivo). Duplicado de import `android.graphics.Bitmap` al editar imports (ya estaba) — limpiar antes de
compilar.

Android **v1.32 (vc14)**, commit `4f8084d`, tag `v1.32`, release
https://github.com/jh4ckt/alkilapp/releases/tag/v1.32. APK instalada en el Xiaomi. Verificación visual
del color del pin pendiente (el mapa mostrará los pines slate; el padre estaba en la ficha al cerrar).

---

## Ronda 19 (2026-09-18): "Destacar" oculto en publicaciones finalizadas (Mis Publicaciones)

El padre: "en mis publicaciones, si el estado es finalizado, ya no debe existir la opcion de destacar
publicacion". En `MisPublicacionesActivity.mostrarMenuOpciones()` el ítem del PopupMenu `menu_destacar`
solo cambiaba el título (Destacar publicacion / Quitar destacado) según `p.esDestacado` pero NUNCA se
ocultaba según estado. Fix: `menu.findItem(R.id.menu_destacar).isVisible = estadoNorm != "finalizado"`.
En `PerfilPropietarioActivity` el botón "Destacar publicación" YA estaba correcto porque vive dentro del
bloque `if (miUid == uid && (esDisponible || esPausada) && ...)` → finalizado nunca muestra ningún botón
del propietario. Build OK. Gotcha adb de la sesión: el dispositivo pasó a "offline" en adb entre builds →
`adb kill-server` + `start-server` (con tiempos) lo restauró a "device"; reintentar el install.

Android **v1.33 (vc15)**, commit `909e3a2`, tag `v1.33`, release
https://github.com/jh4ckt/alkilapp/releases/tag/v1.33. APK instalada en el Xiaomi (la verificación
visual queda en manos del padre: abrir el menú de opciones de una publicación finalizada y ver que no
aparece "Destacar".

---

## Ronda 12 (2026-09-18): Billing integration + eliminar fotos en edición + precios destacar

### App Android (v1.26, vc8)

**Google Play Billing integrado**
- Librería `com.android.billingclient:billing:7.0.0` agregada en `build.gradle.kts`.
- `billing/BillingManager.kt`: clase completa para flujo de compra con 3 SKUs tipo INAPP:
  - `destacar_7d` (7 días)
  - `destacar_15d` (15 días)  
  - `destacar_30d` (30 días)
- `initialize()`, `launchPurchaseFlow(sku, callback)`, `queryPurchases()`, `handlePurchase()` + acknowledgment.
- **Pendiente configuración**: crear los 3 productos en Play Console (tipo "Managed product" / consumible), publicar app en track interno/cerrado/abierto, y reemplazar la lógica de `iniciarPagoDestacado` por `BillingManager.launchPurchaseFlow()`.

**Editar publicación: eliminar fotos subidas**
- `RegistrarPropiedadActivity.renderizarPreviewsFotos(editMode: Boolean)`: en modo edición, cada miniatura muestra botón 🗑️ rojo (overlay `ic_basurero`) en esquina superior derecha.
- Click en botón elimina la foto de `fotosFormulario` y re-renderiza; al guardar (`guardarPropiedad`), la lista actualizada se persiste en Firestore campo `fotos`.
- `cargarDatosParaEditar()` llama a `renderizarPreviewsFotos(true)`.

**Destacar unificado con precios (consistente en 3 pantallas)**
- Dialog con 3 opciones + precios:
  1. "7 días - S/ 6.90"
  2. "15 días - S/ 12.90"
  3. "30 días - S/ 24.90"
- `MisPublicacionesActivity.mostrarDialogoDestacar()` y `PerfilPropietarioActivity.solicitarDestacar()` usan misma lógica.
- Guarda en Firestore: `solicitudDestacar=true`, `destacadoDias`, `destacadoPrecio`.
- **Pendiente**: conectar `BillingManager.launchPurchaseFlow(SKU_DESTACAR_7D/15D/30D, callback)` cuando SKUs estén creados en Play Console y app en track de pruebas.

**Iconos/recursos nuevos:**
- `drawable/ic_check.xml` (Marcar como alquilado)
- `drawable/bg_foto_thumb.xml` (fondo miniaturas)

Version bump: Android **v1.26 (vc8)**, tag `v1.26`, release GitHub. APK instalada en Xiaomi, build OK sin FATAL. Pendiente prueba en vivo del padre + configuración Play Console.

---

## Ronda 13 (2026-09-18): Play Billing launchPurchaseFlow integrado

### App Android (v1.27, vc9)

**Flujo de compra completo integrado en MisPublicacionesActivity y PerfilPropietarioActivity**

1. Usuario toca "Destacar" → dialog 3 opciones (7/15/30 días con precios)
2. App guarda solicitud pendiente en Firestore: `solicitudDestacar=true`, `destacadoSku`, `destacadoDias`, `destacadoPrecio`
3. `BillingManager.launchPurchaseFlow(sku, callback)` abre UI de Google Play
4. **onSuccess(productId, purchaseToken, orderId)**:
   - Calcula `featuredUntil = now + dias * 24h`
   - Actualiza Firestore: `isFeatured=true`, `featuredUntil`, `purchaseToken`, `orderId`, `destacadoAprobadoPor="google-play-billing"`, `solicitudDestacar=false`
   - Toast "¡Destacado activado por X días!"
5. **onError(message)**:
   - Limpia solicitud: `solicitudDestacar=false`, `destacadoSku=null`
   - Toast "Error en compra: {message}"

**SKUs configurados (tipo INAPP / Managed product):**
- `destacar_7d` → 7 días, S/ 6.90
- `destacar_15d` → 15 días, S/ 12.90  
- `destacar_30d` → 30 días, S/ 24.90

**Archivos modificados:**
- `billing/BillingManager.kt`: `launchPurchaseFlow` con `queryProductDetailsAsync` + `BillingFlowParams`
- `MisPublicacionesActivity.kt`: `billingManager` lazy init + `iniciarPagoDestacado` con callback completo
- `PerfilPropietarioActivity.kt`: mismo flujo, consistente

---

## Configuración Play Console (PENDIENTE - hacer por el padre)

**1. En Play Console → Monetizar → Productos → Productos integrados en la app:**
- Crear 3 productos "Producto administrado" (Managed product):
  - ID: `destacar_7d` → Nombre: "Destacar 7 días" → Precio: S/ 6.90 (PEN)
  - ID: `destacar_15d` → Nombre: "Destacar 15 días" → Precio: S/ 12.90 (PEN)
  - ID: `destacar_30d` → Nombre: "Destacar 30 días" → Precio: S/ 24.90 (PEN)
- Todos: "Activo" ✓

**2. En Play Console → Probar y lanzar → Testing (Pruebas):**
- Track recomendado: **Prueba interna** (Internal testing) para iteración rápida
- O **Prueba cerrada** / **Prueba abierta** si ya hay usuarios
- Subir el **AAB** (no APK) generado con `./gradlew.bat bundleRelease`
  - En Android Studio: Build → Generate Signed Bundle / APK → Android App Bundle
  - O CLI: `$env:JAVA_HOME='...'; .\gradlew.bat bundleRelease`
  - Output: `app/build/outputs/bundle/release/app-release.aab`

**3. En el track elegido → Testers:**
- Agregar emails de prueba (el padre, cuentas de test)
- Guardar cambios

**4. Esperar propagación (puede tardar horas):**
- Los productos deben aparecer como "Activos" en la consola
- La app en el track debe estar disponible para descargar

**5. Probar en dispositivo:**
- Desinstalar APK debug actual
- Instalar desde Play Store (track de prueba) con cuenta de tester
- Entrar a Mis Publicaciones → tocar "Destacar" → elegir opción → debe abrir UI de Google Play
- Completar compra (con tarjeta de prueba o real) → debe activar destacado en la app

**Nota importante:** Para probar compras reales sin cobrar, usar **cuentas de prueba con licencias** (License testers) en Play Console → Configuración → Detalles de la cuenta → Probadores con licencia. Estas cuentas pueden hacer compras que se reembolsan automáticamente.

Version bump: Android **v1.27 (vc9)**, tag `v1.27`, release GitHub. APK instalada, build OK. Admin redeployed rev `alkilapp-admin-00009-94d`. Pendiente configuración Play Console + prueba en vivo.

---

## Ronda 14 (2026-09-18): Bottom Sheet minimizable

### App Android (v1.28, vc10)

**Bottom Sheet colapsable/expandible (peek 100dp)**
- El panel inferior con el listado de inmuebles ahora usa `BottomSheetBehavior` nativo de Material Design:
  - `peekHeight = 100dp` (estado colapsado: solo handle + título visible)
  - `draggable = true` (arrastrable hacia arriba/abajo)
  - `hideable = false` (no se puede ocultar completamente)
  - Estado inicial: `STATE_COLLAPSED`
- Usuario puede arrastrar el handle (raya gris) o el panel para expandir/colapsar
- **FAB "mi ubicación"**: se mueve sincronizado con el panel vía callback `onSlide(slideOffset)`

**Archivos modificados:**
- `activity_main.xml`: `bottomSheet` con `app:layout_behavior=BottomSheetBehavior`, `peekHeight=100dp`, `draggable=true`, `hideable=false`
- `MainActivity.kt`: `configurarBottomSheet()` configura behavior, estado inicial `STATE_COLLAPSED`, callback `onSlide` que ajusta `bottomMargin` del FAB

**Play Console**: aún en verificación (no se pudo configurar productos billing)

Version bump: Android **v1.28 (vc10)**, tag `v1.28`, release GitHub. APK instalada en Xiaomi, build OK sin FATAL. Pendiente configuración Play Console + prueba en vivo del padre.

---

## Ronda 15 (2026-09-18): Sheet 60% + centrado de mapa y popup persistente

### App Android (v1.29, vc11)

**Bottom sheet expandido al 60% de la pantalla:**
- `configurarBottomSheet()`: `sheet.layoutParams.height = 0.6 * altoPantalla` (fijo, con `isFitToContents=false`)
- Colapsado = peek 100dp (handle + título); expandido = 60% altura
- `onSlide`: el padding inferior del mapa (`mMap.setPadding`) se actualiza en tiempo real entre 100dp (colapsado) y 60%+16dp (expandido); el FAB "mi ubicación" sube igual
- `onMapReady` → `onMapLoaded`: padding inicial colapsado (100dp+16dp) en lugar del 60% fijo anterior

**Mapa: centrado y popup persistente al seleccionar inmueble:**
- `OnMarkerClickListener`: añade `mMap.animateCamera(newLatLng(marker.position))` → el mapa se centra en el punto (sin cambiar zoom)
- Nuevo campo `idMarcadorSeleccionado: String?`: al reconstruir marcadores (`onCameraIdle` → `actualizarMarcadoresEnZonaVisible()`) se re-aplica selección (icono verde + `showInfoWindow`) → **el popup ya no desaparece al instante** (antes `limpiarMarcadores()` eliminaba el marcador y con él su info window tras cada idle del mapa)
- `limpiarMarcadores()` ya no resetea `idMarcadorSeleccionado`, solo `marcadorSeleccionado = null`

Version bump: Android **v1.29 (vc11)**, tag `v1.29`, release GitHub. APK instalada en Xiaomi, build OK. Pendiente configuración Play Console + prueba en vivo del padre.

---

## Ronda 11b (2026-09-18, misma sesión): Fix destacar + Pausar/Marcar alquilado

### App Android (v1.25, vc7)

**Fix: Destacar dialog**
- `MisPublicacionesActivity.mostrarDialogoDestacar()`: cambiado de `setSingleChoiceItems` a `setItems` — ahora el dialog se muestra, permite seleccionar una opción (7/15/30 días con precios S/ 6.90 / 12.90 / 24.90) y se cierra correctamente.

**Nuevo flujo de estados en menú de publicaciones (consistente en 3 pantallas):**

| Estado actual | Acciones visibles |
|---|---|
| **disponible** | Pausar · Marcar como alquilado · Destacar · Editar · Eliminar |
| **pausada** (usuario) | Reactivar · Marcar como alquilado · Destacar · Editar · Eliminar |
| **under_review** (admin) | *Solo visualización* — NO reactivable por usuario |
| **finalizado** | *Solo visualización* |

**Detalle de cambios:**

1. **Pausar publicacion** (antes "Suspender"): cambia estado a `"pausada"` (user-initiated). Solo visible si `disponible`. Dialog de confirmación.
2. **Reactivar publicacion**: cambia estado a `"disponible"`. Solo visible si `pausada`. NO visible para `under_review` (suspensión admin).
3. **Marcar como alquilado**: cambia estado a `"finalizado"`. Visible en `disponible` y `pausada`. Dialog de confirmación.
4. **Admin suspension (`under_review`)**: se mantiene como estado solo para admin; el usuario NO ve "Reactivar" para este estado.

**Consistencia entre pantallas:**
- `MisPublicacionesActivity`: `PopupMenu` con 6 opciones (Editar, Destacar, Pausar, Reactivar, Marcar alquilado, Eliminar).
- `PropiedadDetalleActivity`: 
  - Botón "Finalizar" visible en `disponible` y `pausada`.
  - Banner `tvDetPausada` nuevo para estado `pausada`.
  - Chat oculto en `pausada` y `finalizado`.
  - `btnFinalizarPub.visibility` actualizado para incluir `pausada`.
- `PerfilPropietarioActivity` (Inmuebles publicados): botones dinámicos según estado:
  - `disponible`: Pausar + Marcar alquilado + Destacar
  - `pausada`: Reactivar + Marcar alquilado + Destacar
  - Otros estados: solo Destacar (si corresponde)

**Layout/strings/iconos nuevos:**
- `activity_propiedad_detalle.xml`: agregado `tvDetPausada` (banner "Publicación pausada").
- `menu_mis_publicaciones.xml`: renombrado `menu_suspender` → `menu_pausar`, agregado `menu_marcar_alquilado` (icono `ic_check`).
- `drawable/ic_check.xml`: nuevo icono check.

Version bump: Android **v1.25 (vc7)**, tag `v1.25`, release GitHub. APK instalada en Xiaomi, build OK sin FATAL. Pendiente prueba en vivo del padre.

---

## Ronda 11 (2026-09-18): Fix edit mode, destacar dialog, ubicación, foto

### App Android (v1.24, vc6)

**Fix: Editar publicación — keys coinciden**
- `MisPublicacionesActivity.editarPublicacion()` ahora pasa `EXTRA_EDIT_MODE` (`edit_mode`) y `EXTRA_PROPIEDAD_ID` (`propiedad_id`) que coinciden exactamente con los `companion object` de `RegistrarPropiedadActivity`. Antes usaba camelCase (`editMode`, `propiedadId`) y no activaba el modo edición.
- Al tocar "Editar" abre `RegistrarPropiedadActivity` con `cargarDatosParaEditar()` que hace `get()` a Firestore y pre-llena todo: spinners (tipo, operación, moneda, distrito), chips comodidades, fotos base64 (render preview), lat/lng, textos.
- `guardarPropiedad(editMode, propiedadId)`: si editMode → `update()` doc existente (mantiene `estado` actual); si nuevo → `add()` con `estado="under_review"`.

**Destacar publicación: dialog con 3 opciones y precios**
- `mostrarDialogoDestacar(p)` muestra `AlertDialog` con `setSingleChoiceItems`:
  1. "7 días - S/ 6.90"
  2. "15 días - S/ 12.90"
  3. "30 días - S/ 24.90"
- `iniciarPagoDestacado(p, dias, precio)` guarda en Firestore: `solicitudDestacar=true`, `destacadoDias`, `destacadoPrecio`. Toast confirma "Solicitud de destacado X días (S/ Y) enviada. Pendiente pago."
- **Pendiente**: integración real Google Play Billing / Google Pay (hoy solo guarda la solicitud).

**Ubicación: notificación para usuarios nuevos**
- `MainActivity.verificarPermisoUbicacion()` llamado al final de `onCreate()`.
- Usa `SharedPreferences("alkilapp_prefs")` clave `location_permission_shown` para mostrar solo una vez.
- Si no tiene `ACCESS_FINE_LOCATION` ni `ACCESS_COARSE_LOCATION` → `AlertDialog` con título "Activar ubicación", mensaje explicando para qué sirve (inmuebles cercanos, distancias, centrar mapa), botón "Activar ahora" → `ACTION_APPLICATION_DETAILS_SETTINGS` (abre ajustes de la app), y "Más tarde".
- Al cerrar, marca `location_permission_shown=true`.

**Foto de perfil visible en ambas pantallas**
- `PerfilPropietarioActivity.cargarUsuario()`: ya existía lógica para `profilePicture` (URL) y `fotoBase64` — se llama `cargarFoto(url)` o `cargarFotoBase64(b64)` que setean `ivPerfilFoto` visible y ocultan `tvPerfilInicial`.
- `MiPerfilActivity.cargarMisDatos()`: ya cargaba `fotoBase64` y llamaba `mostrarFoto(b64)`.
- Ahora en ambos perfiles (propio y ajeno) se ve la foto si el usuario la subió.

Version bump: Android **v1.24 (vc6)**, tag `v1.24`, release GitHub. APK instalada en Xiaomi, build OK sin FATAL. Pendiente prueba en vivo del padre.

**Ronda 7 - 3 pedidos del padre (2026-09-17, APK instalado en el Xiaomi, panel admin redepleagado - SIN probar aun por el padre):**
1. **Admin: aprobar una publicacion → `estado="publicado"`** (antes "disponible"). En `backend/admin/server.js`: el POST `/publicaciones/{id}/aprobar` ahora escribe `{estado:'publicado', aprobadoEn:Date}` + `return` temprano con mensaje. Ademas NUEVO endpoint `/publicaciones/{id}/estado` (`<select>` "Cambiar" por fila en el panel) que acepta `publicado/disponible/finalizado/under_review/pendiente` y deja `estadoCambiadoAdmin=true/estadoCambiadoEn` — el padre pidio "o permitirme cambiar el estado publicado para que aparezca en las publicaciones del usuario". Regex de rutas ampliado. `README.md` del panel actualizado. `node --check` OK. **Redeplegado a Cloud Run: rev `alkilapp-admin-00006-6ft` sirviendo 100%** (`gcloud run deploy alkilapp-admin --source . --region us-central1 --allow-unauthenticated --quiet` desde `D:\alkilapp\backend`; sin `--set-env-vars` para no tocar ADMIN_PASSWORD/SECRET — gcloud conserva las env vars existentes). La app lee `publicado` como `disponible` via `Propiedad.estadoNormalizado`, o sea aparece en el feed y habilita el chat/seguimiento.
2. **Notificaciones de chat nuevo (SIN FCM aun):** lo que llego instalado es un monitor **local** (Firestore snapshot mientras la app esta viva, no push con app cerrada). Archivo nuevo `app/src/main/java/com/alkilapp/ChatNotificaciones.kt`:
   - `object ChatVista` — flags globales: `EN_LISTA="@"` (ChatListActivity abierta) y `actual` (chatId del ChatDetailActivity abierto). `ChatDetailActivity.onStart/onStop` y `ChatListActivity.onStart/onStop` lo setean para NO avisar mientras el usuario ya ve el chat/bandeja.
   - `object NotificadorChat` — canal `alkilapp_chats` ("Chats nuevos", IMPORTANCE_DEFAULT), `mostrar()` con `NotificationCompat` (smallIcon `android.R.drawable.stat_notify_chat`, titulo = nombre del otro participante si ya esta cacheado sino `listingTitle`, texto "Inmueble: <título>" + BigText del `lastMessage`), tap → `ChatDetailActivity` (PendingIntent inmutable, requestCode=chatId.hashCode) con EXTRA_CHAT_ID/EXTRA_LISTING/EXTRA_OTRO_UID. try/catch `SecurityException` si no hay permiso.
   - `object MonitorChats` — arrancado en `App.onCreate` (`MonitorChats.iniciar(this)`): AuthStateListener (conecta cuando hay uid, desconecta al cerrar sesion) + snapshot `chats` `whereArrayContains("participants", uid)`. **Primera lectura siembra `notificados[chatId]=lastMessageAt` SIN avisar** (no re-notificar no-leidos viejos al abrir la app); despues avisa solo si `unreadMio>0 && lastMessageAt>previo && ChatVista.actual!=chatId && !=EN_LISTA`. Prefetch del nombre del otro via `PerfilUsuario.buscar` (cache `nombres`). `unreadCount[me]` lo setea a 0 `marcarLeido` al abrir el chat y `enviarMensaje` solo incrementa el del otro → nunca se auto-notifica uno mismo.
   - Manifest: `<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>` y `MainActivity.onCreate` (SDK>=33) hace `requestPermissions` legacy (code 1001) → dialogo de permisos aparece al abrir la app (el padre debe tocar "Permitir"; Xiaomi/MIUI puede pedir ajustes extra de bateria para avisos en segundo plano). targetSdk 36.
   - **Limitacion conocida**: son notificaciones en-vivo con la app abierta/segundo plano (proceso vivo); con la app matada no llegan (para eso habria que agregar FCM + trigger server-side; no se hizo — documentado aqui como pendiente opcional).
3. **Chats por inmueble: YA CUMPLIDO de fabrica (confirmado en codigo, no req cambio):** cada inmueble tiene su chat determinístico `chats/inm-<listingId>` (lo crean `PropiedadDetalleActivity.abrirChatPropietario()` y `PerfilPropietarioActivity` con `inm-$listingId`, guardando `listingId`+`listingTitle`+`participants`); la bandeja lista un row POR chat con `setText(tvListing)=listingTitle` y badge `unreadMio`; `ChatDetailActivity.tvChatListing` muestra el inmueble y se sincroniza por snapshot. O sea "si estoy alquilando varios inmuebles" = varias conversaciones separadas, cada una con su departamento en cabecera. Nota: los seeds `chat-papa-renzo`/`chat-papa-diego` y `chat-demo-01` son por-propietario con listingId seed (id determinista distinto de `inm-*`) — solo dato de demo.

**Ronda 8 - filtros avanzados (2026-09-17, APK instalado, sin probar por el padre):** el dialogo de filtros (`dialog_filtros.xml`) gano 2 spinners nuevos (pedido del padre): **Tipo de inmueble** (`spFiltroTipo` = Todos + `R.array.tipos_inmueble`: departamento/casa/habitacion/otro) y **Cantidad de habitaciones** (`spFiltroHabitaciones` = Todos/1/2/3/4 o más; `4 o más` = `ambientes >= 4`). Estado nuevo en MainActivity: `filtroTipo: String?` y `filtroHabitaciones: Int?` (4 = 4+); se aplican en `adapter.setFiltros(dep, dis, tipo, hab)` (firma extendida con default null — el unico caller es MainActivity) y se limpian en "Limpiar filtros". `actualizarBotonFiltros` cuenta los filtros nuevos. En `PropiedadAdapter.aplicar()` las condiciones nuevas: `tipoClave(p.tipo) == filtroTipo` y `(if (filtroHabitaciones == 4) ambientes >= 4 else ambientes == filtroHabitaciones)`. `tipoClave()` normaliza (lowercase + sin acentos; contiene habitacion/cuarto→habitacion, departamento/depto→departamento, casa→casa, else→otro) para que datas free-form del seed ("Departamento de 2 cuartos") matcheen. Strings nuevos `filtros_tipo`, `filtros_habitaciones`, array `opciones_habitaciones`. La busqueda/zona/centrado del mapa NO cambian (solo tipo/hab no mueven el mapa). Build OK, instalado, sin FATAL.

**Ronda 5 (2026-09-17, app instalada en el Xiaomi; SIN probar todavía por el padre):**
- **Crash del feed RESUELTO (root cause por logcat, no adivinado):** al tocar un inmueble de la lista filtrada crasheaba con `TransactionTooLargeException: data parcel size 1189640 bytes` — `MainActivity.abrirDetallePropiedad` metía las fotos base64 (~1.2 MB) en el `Intent` y el límite del Binder es 1 MB (solo pasaba con inmuebles con fotos, p.ej. "cuarto en Comas"). Fix: `PropiedadDetalleActivity` recibe SOLO el id y carga fotos de Firestore (`cargarFotosDesdeFirestore()`, lee `fotos` y `photos`); `EXTRA_FOTOS` eliminado de MainActivity y PerfilPropietarioActivity; `armarGaleria()` idempotente.
- **Design system aplicado (paleta web/Tailwind) a todo el app Android:** `colors.xml` reescrito con tokens semánticos (`brand_accent #FF6B5E` coral, `brand_primary #1E293B` slate, `bg_main #F8FAFC`, `surface_card`, `border_subtle #E2E8F0`, `text_muted #64748B`, `status_success #3AA37B`, `brand_gold #D4A017`, `brand_gold_soft #FBF1D6`) y **alias de compatibilidad** (`alkil_primary`, `alkil_coral`, `alkil_mint`, `alkil_gold`...) mapeados a la paleta nueva, así TODA la app pasó de azul a coral/slate sin tocar cada layout. `themes.xml` con colorPrimary/colorSecondary/colorBackground/colorSurface nuevos + estilos `AlkilAppCircle` y `AlkilAppRounded`. Regla 60-30-10.
- **Property Card (`item_propiedad.xml`) rediseñada:** imagen 150dp con corazón de favorito, badge de estado (`bg_pill_estado`), etiqueta "Destacado" (`bg_etiqueta_destacado`), detalles con iconos de habitaciones/m² (`ic_cama`/`ic_area`), CTA "Ver detalles" y borde `border_subtle`. **Destacado en dorado** (`brand_gold` en borde + etiqueta) por pedido explícito del padre. Archivos nuevos: `data/Favoritos.kt` (SharedPreferences `alkilapp_favoritos`), `drawable/ic_corazon`, `ic_corazon_lleno`, `ic_cama`, `ic_area`, `bg_favorito`, `bg_pill_estado`; `data/Propiedad.kt` ganó `banios`.
- **Orden del feed (decisión del padre): tier y luego cercanía.** `PropiedadAdapter.aplicar()` ahora ordena por `tier()` = destacado(0) → propietario verificado(1) → sin verificar(2) y dentro de cada grupo por `distanciaKm` (haversine). `MainActivity.cargarVerificacionPropietarios` marca verificado si `verificationBadge == true` **o** `verification.identityVerified == true` (nuevo).
- **Login/registro nuevo (`AuthActivity` + `activity_auth.xml`):** pantalla full-screen que reemplaza el viejo `dialog_auth`; `MainActivity.abrirDialogoAutenticar()` ahora solo lanza el Activity (los métodos viejos de auth quedan sin uso). Tabs Ingresar/Crear cuenta (`MaterialButtonToggleGroup`), nombre + repetir contraseña solo en registro, errores inline (`tvAuthError`), botón Google (reusa `default_web_client_id`), "Olvidé mi contraseña" (`sendPasswordResetEmail`), bloqueo del form mientras procesa, y `guardarUsuarioEnBase()` que no pisa el nombre existente.
- **Verificación de identidad con documento (`VerificacionActivity` + `activity_verificacion.xml`):** entrada desde `MiPerfilActivity` (`btnVerificarIdentidad`). Tipo (DNI/Carnet de extranjería/Pasaporte) + número + foto del documento (comprimida a ~1000px q75, base64). El documento va a **`verificaciones/{uid}`** (lo revisa el panel admin) y el resumen a **`usuarios/{uid}.verification`** (`status`, `documentoPendiente`, `tipoDocumento`, `numeroDocumento`, `enviadoEn`) — se decidió NO duplicar el base64 en el doc del usuario. Muestra el estado actual (en revisión / verificado / rechazado + motivo) y bloquea el reenvío si ya está verificado.
- **Panel de administración desplegado en Cloud Run (decisión del padre: "en la nube"):** `backend/admin/server.js` — Node puro (sin Express, solo `http` + `@google-cloud/firestore`) con login por `ADMIN_PASSWORD` (cookie HMAC firmada con `ADMIN_SECRET`), páginas Resumen / Verificaciones (con la foto del documento + aprobar/rechazar con motivo) / Publicaciones (aprobar `under_review`, destacar 30 días, finalizar) / Denuncias (resolver) / Usuarios (identidad, trustLevel, rating). Aprobar una verificación setea `identityVerified=true`, `verificationBadge=true` y sube `trustLevel` a `verified`. **URL: https://alkilapp-admin-288429280561.us-central1.run.app** (password en `%TEMP%\opencode\admin-pw.txt`; rotarla con `gcloud run services update alkilapp-admin --update-env-vars ADMIN_PASSWORD=...`). Service account `alkilapp-admin@gen-lang-client-0040505884.iam.gserviceaccount.com` con `roles/datastore.user`; usa ADC (el JSON local se detecta solo si existe, y `.dockerignore` lo excluye de la imagen). Gotcha del deploy: `backend/package.json` NO tenía `scripts.start`, así que el buildpack intentaba el `main` (`seed.js`) y la revisión fallaba con "container failed to start" → se agregó `"start": "node admin/server.js"`.
- **Reglas v7 (ruleset `8852e34c-2a10-44b7-a62c-b48cca282fb7`):** se agregó `verificaciones/{uid}` (el usuario lee/crea/actualiza SOLO la suya, `imagen` string ≤ 900000 chars; el SA/admin omite las reglas y es quien cambia `estado`). Además ahora las reglas viven en el repo: **`backend/firestore.rules`** + herramienta **`backend/reglas.js`** (`node reglas.js show` imprime las activas, `node reglas.js deploy <archivo>` publica con el patrón DELETE+POST del release; `npm run reglas`). Las reglas ya NO están solo en la API.
- **Bug encontrado por el padre probando el panel (mismo día):** al aprobar salía `Error: db.collection(...).document is not a function`. `@google-cloud/firestore` (a diferencia de `firebase-admin`) NO tiene `.document()`, solo `.doc()` — el resto de los scripts del backend ya usaban `.doc()`, el panel no. Corregido en `backend/admin/server.js` (6 ocurrencias) y redeployado (revisión `alkilapp-admin-00003-7rg`). Probar SIEMPRE las acciones (no solo que la página cargue) antes de dar por bueno el panel: el `GET` funcionaba igual con el bug. Regla general: en este backend usar `.doc()`, nunca `.document()`.
- Pendientes: el padre debe probar en vivo (registro/login, subir documento, panel admin); push a GitHub + release; iOS de AlkilApp.

**Ronda 6 (2026-09-17, app instalada en el Xiaomi; sin probar aún por el padre):**
- **Fotos del listado pixeladas (pedido en vivo):** root cause = `PropiedadAdapter.decodificarThumb` decodificaba a **144 px fijos** (más chico que el ImageView de 150dp), así que la tarjeta estiraba una miniatura diminuta. Reescrito: objetivo `dp(420)`, decodificación en dos pasadas (`inJustDecodeBounds` + nueva helper `factorMuestra()`) y `createScaledBitmap` al tamaño real (nunca agranda). Cache LRU 8 MB intacto.
- **Crash al maximizar una foto (pedido en vivo):** `FotoZoomActivity` recibía el base64 por `Intent` → mismo `TransactionTooLargeException` del Binder 1 MB (el fix del feed solo había cubierto la ficha, no el visor). Reescrito: recibe `EXTRA_PROPIEDAD_ID` + `EXTRA_POSICION`, carga `fotos`/`photos` de Firestore, muestra `pbZoom` (ProgressBar nueva en `activity_foto_zoom.xml`) y decodifica con `inSampleSize` a `LADO_MAX = 2048` en background (executor + `holder.vista.tag` para descartar resultados viejos al reciclar). `PropiedadDetalleActivity.abrirZoomFoto()` pasa el id (sale si está vacío). Regla general del proyecto: **nunca pasar base64 de fotos por Intent, siempre el id + carga de Firestore.**
- **Estados normalizados en el app:** `Propiedad.estadoNormalizado` (nuevo getter) trata `""`, `"publicado"` y `"activo"` como `"disponible"`; usado en el badge de `PropiedadAdapter`, en `PropiedadDetalleActivity` (chat + finalizar) y en `PerfilPropietarioActivity` (`estadoChip`, "Ya lo alquilé"). Motivo: el panel/admin podía dejar `estado` vacío o `"publicado"` y esas publicaciones quedaban sin badge y sin chat. Al aprobar el panel ahora escribe explícitamente `estado = "disponible"` (antes `"publicado"`).
- **Panel admin: eliminar + aprobar→disponible + destacados con días.** `vistaPublicaciones()` reescrita: botón **Eliminar** con `onsubmit="return confirm(...)"` (el título se le pasa sin comillas simples para no romper el JS del atributo), pill dorado con los días restantes + "vence <fecha>", pill "pidió destacar (N días)", y el botón de destacar muestra la duración pedida (default "Destacar 30 días"). Nueva ruta POST `/publicaciones/:id/eliminar` (`ref.delete()`); `/publicaciones/:id/aprobar` ahora setea `{ estado: 'disponible' }`; `/publicaciones/:id/destacar` usa `destacadoDias` (el pedido por el usuario, o 30) en vez de 30 fijos y escribe `destacadoDias`, `destacadoDiasRestantes`, `destacadoEstado`, `destacadoAprobadoEn/Por`.
- **Destacar 7/15/30 días (pedido del padre):** en el app, `PerfilPropietarioActivity.solicitarDestacar()` ahora abre un diálogo de opción única 7/15/30 (default 30) y `enviarSolicitudDestacar(p, dias)` guarda `solicitudDestacar=true`, **`destacadoDias`**, `solicitudDestacarEn/Por`. Strings nuevos: `perfil_destacar_mensaje`, `perfil_destacar_dias`, `perfil_destacar_solicitar`; `perfil_destacar_solicitud_enviada` ahora lleva `%1$d`. En `backend/destacar.js`, `aprobarDestacado(id, dias = null)` respeta `destacadoDias` de la solicitud si no se pasa `--dias` (antes el CLI forzaba 30) y `quitarDestacado()` resetea `destacadoDiasRestantes = 0`.
- **Caducidad automática de destacados:** nuevo `backend/expirar.js` exporta `expirarDestacados(db)` (idempotente, `require.main === module` guard para reusarlo desde el panel): a los `isFeatured == true` vencidos los apaga (`isFeatured=false`, `destacadoEstado='expirado'`, `destacadoDiasRestantes=0`, `featuredUntil=null`) y a los vigentes les recalcula el contador. CLI: `npm run expirar`. Endpoint en el panel **`GET /cron/expirar?clave=<ADMIN_SECRET>`** — va ANTES del gate de sesión (Cloud Scheduler no puede loguearse) y devuelve JSON; sin clave o con clave mala → 403. **Cloud Scheduler creado**: job `alkilapp-expirar-destacados` en us-central1, `0 3 * * *` `America/Lima`, GET a esa URL. **Verificado de verdad**: se cambió la schedule a un minuto futuro, disparó (200 con user-agent `Google-Cloud-Scheduler` en los logs de Cloud Run) y se restauró a las 3am. Ojo: `gcloud scheduler jobs run` devolvió exit 0 sin ejecutar nada (no dejaba `lastAttemptTime`), así que la única prueba confiable es mover la schedule + mirar logs.
- **Panel redesplegado** dos veces: revisiones `alkilapp-admin-00004-6pr` y `alkilapp-admin-00005-f7v` (esta última con el fix del título en el confirm). `admin/README.md` documenta los destacados con duración, el barrido y el alta del job de Cloud Scheduler.
- Pendientes: el padre debe probar en vivo (fotos, visor, diálogo de días, panel: aprobar/eliminar/destacar); push a GitHub + release; iOS de AlkilApp.

**Ronda 7 (2026-09-17, app compilada e instalada en el Xiaomi; pendiente de prueba visual del padre):**
- **Menu hamburguesa rediseñado (pedido: "que no sea el pop-up flotante"):** el `PopupMenu` viejo se reemplazó por un **drawer lateral**. Nuevo `res/layout/panel_menu.xml` (300dp): cabecera con avatar circular (`bg_nav_avatar`) + nombre/email/hint de sesión sobre `bg_nav_header` (gradiente brand_primary→primary_dark), filas `btnNavPerfil`, `btnNavChat`, `btnNavFiltros`, divider, `btnNavPublicar`, y al pie `btnNavSalir` (solo con sesión) + footer. Drawables nuevos: `bg_panel_menu` (esquinas derecha 24dp), `bg_nav_header`, `bg_nav_avatar`, `ic_logout`. `activity_main.xml` raíz ahora es `DrawerLayout` (`@+id/drawerLayout`) con `<include @+id/panelMenu layout_gravity="start">`. `MainActivity.configurarMenu()` abre el drawer (`GravityCompat.START`) y cablea las filas; `actualizarUiSesion()` puebla la cabecera (nombre/email/inicial) y muestra/oculta `btnNavSalir`; nuevo `cerrarSesion()`. `res/menu/menu_principal.xml` ELIMINADO (ya no se usa PopupMenu). Insets del panel aplicados en `configurarInsetsSistema()` (`panelMenuRoot` bottom, `llNavHeader` top).
- **Contraste de texto (pedido en vivo: "en algunas zonas la letra se confunde con el fondo"):** causa raíz = el tema era `Theme.Material3.DayNight.NoActionBar` y **no existía `values-night`**, con lo que el modo oscuro del teléfono invertía fondos y dejaba textos claros sobre blanco. `themes.xml` → `Theme.Material3.Light.NoActionBar` + `android:forceDarkAllowed=false` (en `values/`, API 29+) + tokens M3 (`colorOnSurfaceVariant`, `colorSurfaceVariant`, `colorOutlineVariant`, `colorSurfaceContainer*`, `colorSurfaceInverse`, `colorOnBackground`, `android:textColorHint`). Nuevos colores en `colors.xml`: `text_hint #64748B`, `on_dark_soft #CBD5E1`, `gold_text #8A6A00`, `success_text #1F7A57`, `status_success_dark #237A58`, `coral_text #C0392B` y `brand_accent_deep #D24534`. **Regla de contraste aplicada:** los tonos de marca NO sirven como texto sobre blanco ni con texto blanco encima (coral #FF6B5E = 2.7:1) → texto coral usa `coral_text` (5.4:1) y todo fondo coral con texto blanco (7 botones: auth, mi_perfil, chat propietario, registrar, ubicacion, verificacion, "Ver detalles") usa `brand_accent_deep` (4.5:1). Chips/datos verdes → `success_text`/`status_success_dark`; "En revisión" → `gold_text`. Burbujas del chat ya estaban bien (slate + blanco = 14:1).
- **Correo del propietario oculto en la ficha (pedido del padre):** eliminado `tvDetOwnerContacto` del layout, su asignación en `PropiedadDetalleActivity.cargarPropietario()`, la constante `EXTRA_CONTACTO` (en detalle y en `PerfilPropietarioActivity`) y todos sus `putExtra` (MainActivity, PerfilPropietarioActivity); `RegistrarPropiedadActivity` ya no persiste `contacto` en la propiedad. En `PerfilPropietarioActivity` el bloque teléfono/email quedó en `GONE`. El contacto queda solo detrás del botón "Chatear con el Propietario".
- **Verificación estructural (sin poder inyectar toques: `adb input` sigue bloqueado por INJECT_EVENTS):** con un extra temporal (`abrir_menu_debug`) se comprobó por `dumpsys activity top` que el drawer abre de verdad (`panelMenu` pasa de `-844,0-0,2400` a `0,0-844,2400`) y por `uiautomator dump` que las filas caen ordenadas sin solapes (Mi perfil y≈465, Mis chats ≈611, Filtros ≈757, Publicar ≈952, footer ≈2147; sin sesión no aparece "Cerrar sesión"). **Ese extra temporal ya se quitó** y el APK se recompiló/instaló; arranque sin FATAL en logcat.
- **Gotcha nueva de build (importante):** NO usar `Set-Content -Encoding UTF8` de PowerShell 5.1 para editar XML de `res/` — escribe **BOM** y AAPT2 falla con `Resource and asset merger: Cannot read field "elmName" because "root" is null`. Para reescrituras masivas usar `[System.IO.File]::WriteAllText($p, $t, (New-Object System.Text.UTF8Encoding($false)))`. Los archivos quedaron corregidos y el build pasó.
- Pendientes: prueba visual del padre (drawer, contraste en listado/ficha/chat/perfil, correo oculto); push a GitHub + release; iOS de AlkilApp.
- **Segundo lote 2026-09-17 (pedidos en vivo, compilado+instalado, reglas desplegadas):**
  - **Popup del mapa (InfoWindow):** ahora muestra el **tipo** de inmueble en la linea grande (antes el titulo) y la tercera linea pasa a ser solo la zona; el marcador seleccionado pasa de **cyan a verde medio** (`HUE_GREEN` en `initIconosMarcadores`, antes `HUE_CYAN`).
  - **Favoritos en la base + filtro "Solo favoritos":** `Favoritos.kt` reescrito — sigue con cache local (SharedPreferences, modo sin sesion) y ahora replica en **Firestore coleccion `favoritos`** con doc `<uid>__<propiedadId>` (`uid`, `propiedadId`, `creadoEn` serverTimestamp). `sincronizar()` trae los de la nube al iniciar sesion (en `onStart` y tras login Google/email); `cerrarSesion()` vuelve al cache local y apaga el filtro. `PropiedadAdapter` recibe `onAlternarFavorito` callback + `setFavoritos`/`setSoloFavoritos`; el corazon requiere sesion (si no: toast + login). Diálogo de filtros: nuevo CheckBox "Solo favoritos" (`cbSoloFavoritos`). **Rules v8** (ruleset `3e26c264-08c7-434e-be91-f55d53892701`): regla `favoritos/{favorito}` — lectura solo propia (doc id `^<uid>__`), create/update/delete solo del dueño; desplegada con `node reglas.js deploy firestore.rules`.
  - **Menú hamburguesa:** dos filas nuevas debajo de "Publicar inmueble" en el mismo coral (`coral_text`): **"Ver mis publicaciones"** (`btnNavMisPublicaciones`, icono nuevo `ic_lista` → abre `PerfilPropietarioActivity` con EXTRA_UID del usuario) y **"Verificar identidad"** (`btnNavVerificar`, icono `ic_verificado_escudo` → abre `VerificacionActivity`). Ambas piden login si no hay sesion. Strings nuevos: `nav_verificar_identidad`, `favoritos_requiere_sesion`, `filtros_solo_favoritos`.
  - **Destacar con días:** YA estaba hecho en Ronda 6 (dialogo 7/15/30 en `solicitarDestacar()` + panel respeta `destacadoDias`) — el padre repitió el pedido pero no falta nada por agregar; solo hay que probarlo en vivo.
  - Verificado: build OK sin errores, instalado, arranque sin FATAL, `dumpsys activity top` muestra las 3 filas coral del drawer ordenadas (Publicar 487-633, Mis publicaciones 633-779, Verificar 779-925). Reglas desplegadas OK (v8). Pendiente prueba visual del padre.

- **Tercer lote 2026-09-17 (pedidos en vivo, compilado+instalado, sin FATAL):**
  - **"Mi perfil" limpio:** eliminadas de `activity_mi_perfil.xml` y de `MiPerfilActivity.kt` las opciones "Ver mis publicaciones" (`btnVerPublicaciones`) y "Verificar identidad" (`btnVerificarIdentidad`) y su metodo `verMisPublicaciones()`. Ese acceso ahora vive solo en el drawer.
  - **Pantalla estatica "Mis publicaciones" (NUEVA `MisPublicacionesActivity.kt` + `activity_mis_publicaciones.xml`, registrada en el Manifest):** estilo `RegistrarPropiedadActivity` (header full-screen con flecha back `btnMisPubBack` + titulo "Mis publicaciones", no popup). Escucha en vivo `propiedades` where `idPropietario==uid` (sin orderBy, no exige indice), orden: disponibles primero y luego precio. Cards con titulo + `precioFormateado` + chip de estado (`estadoChip` replicado: Disponible coral / Finalizado gris / "En revision" dorado) + linea `tipoMostrable · zona`. Tap → `abrirDetalle()` con el MISMO bloque de extras de MainActivity (EXTRA_ESTADO incluido → la ficha permite "Ya lo alquilé"/finalizar). Estado vacio: `tvMisPubVacio` "Aun no has publicado inmuebles". Strings nuevos: `titulo_mis_publicaciones`, `mis_pub_sesion`, `mis_pub_cargando`, `mis_pub_vacio`. **El drawer `btnNavMisPublicaciones` ahora abre `MisPublicacionesActivity`** (antes `PerfilPropietarioActivity` con EXTRA_UID). Gotcha: `p.fotosUrl` NO existe en `Propiedad` — el campo real es `photosUrl`; y `Int.dp` es extension PRIVADA por archivo (cada Activity la declara).
  - **Popup del mapa AHORA muestra el tipo (root cause encontrado):** `construirInfoWindow()` era **codigo muerto** — grep confirmo que NO existia ninguna llamada a `setInfoWindowAdapter` en el proyecto, asi que el popup visible era el InfoWindow DEFAULT de Google Maps con `marker.title = p.titulo` (por eso el padre veía el nombre). Ahora en `onMapReady`: `mMap.setInfoWindowAdapter(...)` → `getInfoWindow(marker)` busca la `Propiedad` por `marker.tag` (los marcadores del feed ya la ponian; se agrego `.apply { tag = p.id }` tambien a los de `actualizarZonaMapa()` Linea 517/806). El popup custom muestra: **tipo normalizado** (`tipoMostrable`: habitacion→"Habitacion", departamento→"Departamento", casa→"Casa", resto→"Otros" — el padre: "solo 4 opciones en la base") + precio grande + zona + boton "Ver propiedad"; `setOnInfoWindowClickListener` abre la ficha (los clicks en children del InfoWindow NO llegan, solo el listener del window).
  - Verificado: build OK, `adb install -r` OK, arranque sin FATAL ni crash (logcat limpio, MainActivity resumida). Pendiente prueba visual del padre (popup con tipo, pantalla Mis publicaciones, mi perfil sin los 2 botones).
- **Tercer lote - correcciones (2026-09-17, mismo dia, instalado):**
  - **Popup del mapa sin foto:** el padre pidió quitarla ("para que no ocupe mucho espacio") — eliminado el bloque de `BitmapFactory`/img 120dp de `construirInfoWindow`; queda tipo + precio + zona + boton "Ver propiedad".
  - **Mis publicaciones vacio (diagnóstico con datos reales):** consulta al `alkilappdb` con la SA confirmo que las 5 publicaciones del padre viven bajo `idPropietario=smpJfxQT0vUMaCMalJWUej1qPnw1` (uid Google de `presupuestos@oleohidraulic.com`) — si entra con otra cuenta (jhoner.qt `OSj0...` / jeremy.qr `5bDF...` son los otros usuarios del dump), el where devuelve 0. No hubo crash (se abrio y cerro MisPublicacionesActivity 23:28). Para no adivinar: la pantalla ahora muestra arriba **"Cuenta: <email>"** (`tvMisPubCuenta`), agrega logs (`Log.i/w "AlkilApp"` con uid, docs, error) y muestra el mensaje de error en pantalla si falla el snapshot (`mis_pub_error`). Reinstalado; falta que el padre reabra y verificar por logcat si `docs=5` o `uid` no coincide.
  - **VerificacionActivity header:** faltaba `android:fitsSystemWindows="true"` (el back quedaba bajo la barra de estado y no se tocaba) — alineado al estilo de MisPublicaciones/Registrar.
  - **ROOT CAUSE "Mis publicaciones vacio" (2026-09-17, encontrado con logs del cel + dump de la DB):** el padre YA estaba con la cuenta correcta (`smpJfxQT0vUMaCMalJWUej1qPnw1`, Google de presupuestos) y el `whereEqualTo("idPropietario", uid)` desde el cel devolvía 0 sin error, mientras la SA devolvía 5. Logs (`Log.i "AlkilApp"`) dieron `uid=smpJ... docs=0`. **Bug MÍO: `MisPublicacionesActivity.kt` usaba `FirebaseFirestore.getInstance()` (base `(default)`) en vez de `FirebaseFirestore.getInstance("alkilappdb")`** — TODAS las demás actividades/`Favoritos.db()` usan la variante con databaseId; la copie mal en la activity nueva. Fix: `by lazy { FirebaseFirestore.getInstance("alkilappdb") }`. GOTCHA a recordar: en esta app NUNCA usar `getInstance()` pelado — siempre `getInstance("alkilappdb")`. **CONFIRMADO EN VIVO por el padre: "perfecto, ya aparece" — tercer lote + correcciones completos.**

---

## Ronda 9 (2026-09-18, compilado+instalado en el Xiaomi, admin redeployed — SIN probar por el padre):

### 9a) Filtros → Bottom Sheet compacto (sin parecer ventana nueva)
- Reemplazo completo del `MaterialAlertDialogBuilder` + `dialog_filtros.xml` por `MaterialBottomSheetDialog` + **nuevo layout `bottom_sheet_filtros.xml`** (compacto, rounded top 24dp, handle pill superior).
- Diseño en 2 columnas: **Tipo de inmueble** + **Habitaciones** lado a lado (weight 1 cada uno, labels 12sp secondary, spinners 44dp con `bg_input_detalle`).
- Checkbox "Solo favoritos" + botones: **Limpiar** (TextButton coral_dark) y **Aplicar filtros** (filled alkil_coral, minWidth 120dp, rounded 12dp).
- `dismissWithAnimation = true`, spinners rellenados con mismos arrays (`tipos_inmueble`, `opciones_habitaciones`).
- Eliminado `dialog_filtros.xml` (ya no referenciado). Import añadido: `com.google.android.material.bottomsheet.BottomSheetDialog`.

### 9b) Panel Admin — columnas de fecha en 4 tablas
- **Publicaciones**: nueva columna "Publicado" (tras Precio) con `createdAt` del doc o fallback a `createTime` de Firestore.
- **Denuncias**: nueva columna "Creado" (tras Inmueble) con `createdAt` (ya lo escribe la app via `FieldValue.serverTimestamp()`).
- **Verificaciones**: ya mostraba "enviado {fecha(v.createdAt)}"; añadido fallback a `createTime` para docs sin campo propio.
- **Usuarios**: nueva columna "Alta" (tras Confianza) con `createdAt` del seed o fallback `createTime`.
- Helpers en `server.js`: `mostrarCreado(doc)` + `ponerCreado(snapshot)` que inyecta `_creado` desde `createTime.toDate()`.
- Deploy Cloud Run: rev `alkilapp-admin-00007-bj7` sirviendo 100% en https://alkilapp-admin-288429280561.us-central1.run.app.

Build OK, APK instalado, admin redeployed; pendiente prueba en vivo del padre.

---

## Fix post-Ronda 9 (2026-09-18): MisPublicaciones click abre detalle

El padre reportó: "cuando un usuario intenta ver sus publicaciones, no le permite ver la información de la publicación, lo envía a la pantalla principal".

**Root cause probable:** `PropiedadDetalleActivity` leía `EXTRA_FOTOS`/`EXTRA_FOTOS_URL` solo con `getStringArrayListExtra`, pero `MisPublicacionesActivity` y `PerfilPropietarioActivity` los pasaban como `String[]` (`toTypedArray()`). Al recibir `null`, las listas quedaban vacías pero no causaban crash; sin embargo, si `EXTRA_ID` venía vacío por alguna razón, la carga de fotos desde Firestore podía fallar silenciosamente. Añadida validación temprana de `propId` con `finish()` y toast de error.

**Cambios:**
- `PropiedadDetalleActivity.kt`: lectura dual de arrays (`getStringArrayExtra` + fallback `getStringArrayListExtra`); validación `propId.isBlank()` → log + toast + `finish()`.
- `MisPublicacionesActivity.kt`: `Log.i` + `Toast` en `abrirDetalle()` para confirmar click; `MaterialCardView.isFocusable = true`.
- Version bump: Android **v1.22 (vc4)**, tag `v1.22`, release GitHub creado. APK instalada en Xiaomi, build OK.

---

## Ronda 10 (2026-09-18): Menú MisPublicaciones + Admin search

### App Android (v1.23, vc5)
**MisPublicacionesActivity: menú contextual por tarjeta**
- `PopupMenu` (3 puntos `ic_more_vert`) en cada card con 5 opciones:
  1. **Editar** → abre `RegistrarPropiedadActivity` en modo edición (`EXTRA_EDIT_MODE=true`, `EXTRA_PROPIEDAD_ID`) con todos los datos pre-llenados (spinners, chips comodidades, fotos base64, ubicación).
  2. **Destacar / Quitar destacado** → `solicitudDestacar=true` + `destacadoDias=30` / update `isFeatured=false` + limpiar campos destacado.
  3. **Suspender** → `estado="under_review"` (solo visible si `estadoNormalizado=="disponible"`).
  4. **Reactivar** → `estado="disponible"` (visible si `under_review` o `pendiente`).
  5. **Eliminar** → `AlertDialog` confirmación → `delete()` doc en Firestore.
- Iconos vectoriales nuevos: `ic_editar`, `ic_pausa`, `ic_play`, `ic_basurero`, `ic_more_vert` (más `ic_star` existente).

**RegistrarPropiedadActivity: modo edición**
- Nuevos extras en `companion object`: `EXTRA_EDIT_MODE`, `EXTRA_PROPIEDAD_ID`, `EXTRA_TITULO`, `EXTRA_DESCRIPCION`, `EXTRA_TIPO`, `EXTRA_OPERACION`, `EXTRA_PRECIO`, `EXTRA_MONEDA`, `EXTRA_DIRECCION`, `EXTRA_BARRIO`, `EXTRA_CIUDAD`, `EXTRA_AMBIENTES`, `EXTRA_SUPERFICIE`, `EXTRA_COMODIDADES`, `EXTRA_FOTOS`, `EXTRA_FOTOS_URL`.
- `onCreate`: detecta `editMode` → `cargarDatosParaEditar(propiedadId)` hace `get()` doc y pre-llena todos los campos (incluye selección en spinners, chips checked, renderizado previews fotos base64).
- `guardarPropiedad(editMode, propiedadId)`: si editMode → `update()` doc existente (mantiene estado actual); si nuevo → `add()` con `estado="under_review"`.
- Layout `activity_registrar_propiedad.xml`: agregado `android:id="@+id/tvRegistrarTitulo"` al TextView del header para cambiar título a "Editar publicacion".

### Admin panel (rev `alkilapp-admin-00008-jgw`)
**Búsqueda/filtrado en 4 vistas** (helper `buscarInput()` reutilizable):
- **Verificaciones**: filtra en memoria por `email`, `nombre`, `numeroDocumento` (query param `q`).
- **Publicaciones**: filtra por `titulo`, `direccion`, `barrio`, `ciudad`, `idPropietario`.
- **Denuncias**: ya tenía columna "Creado", se mantiene.
- **Usuarios**: filtra por `nombre`, `email`.
- Todas: input `<input type="text" name="q">` + botón "Buscar" + "Limpiar" (si hay query).

Version bump: Android **v1.23 (vc5)**, tag `v1.23`, release GitHub. APK instalada, admin redeployed sirviendo 100%. Pendiente prueba en vivo del padre.

**Ronda 4 pedidos en vivo (2026-09-15, todos TESTEADOS en vivo por el padre):**
1. **Letra más oscura:** `text_secondary` en colors.xml de `#6B7280` a `#374151` (era el gris claro "casi no se ve" de perfiles/hints). Nueva `alkil_coral_soft #FDE8EA` para chips/botones de finalizado.
2. **Perfil del usuario muestra sus propiedades publicadas:** sección "Inmuebles publicados" en `PerfilPropietarioActivity` (header + `tvPerfilInmueblesVacio` + `llPerfilInmuebles`) entre la tarjeta de contacto y Reseñas. `escucharInmuebles()` = snapshot `propiedades` where `idPropietario==uid` (sin orderBy para no exigir índice compuesto), tarjetas `construirInmueble()` con título + `precioFormateado` + chip de estado (`estadoChip()`: Disponible=coral_soft/coral_dark, Finalizado=divider/text_secondary, else "En revisión"=gold_soft/gold) reusando `bg_chip_info` tintado (backgroundTint SRC_IN pinta sobre el sólido). Tap en tarjeta → abre la ficha con el MISMO bloque de extras que MainActivity (`abrirDetalle()` + `EXTRA_ESTADO`). Botón "Ya lo alquilé" (`prop_finalizar_btn`) SOLO si `miUid==uid && estado=="disponible"` → `confirmarFinalizar()` (dialog confirm → `update("estado","finalizado")`).
3. **"Dar por finalizada la publicación" (ficha):** `PropiedadDetalleActivity` recibe nuevo `EXTRA_ESTADO` (lo pasan MainActivity y PerfilPropietarioActivity). Si `esMio && estado=="disponible"` muestra `btnFinalizarPub` (outlined rojo, coral_soft) → `confirmarFinalizar()` (dialog `detalle_finalizar_*` → update estado). Si `estado=="finalizado"`: banner `tvDetFinalizado` ("Publicación finalizada — este inmueble ya fue alquilado", coral_soft) + se esconde `btnChatPropietario`. El update revierte la UI en vivo (button gone + banner visible).
4. **FAB "mi ubicación" acompaña al bottomSheet:** ya NO se tapa al deslizar el listado. `configurarFabSobreBottomSheet()` en MainActivity: `BottomSheetBehavior.from(binding.bottomSheet)` + callback `onSlide` → `fabMiUbicacion.translationY = -slideOffset * 250dp` (diferencia expanded 350 − peek 100). Posición inicial según `sheet.state == STATE_EXPANDED` (slidOffset no es getter público en el material de este proyecto → error de compilación "Unresolved reference: slideOffset"). MarginBottom 120dp = peek 100 + FAB 56 + 20 de aire; arriba queda 20dp sobre el sheet expandido.
- **Rules v6 (ruleset `bae8ad21-7e9e-485e-aec6-f1c4a79b721a`, release `cloud.firestore`):** `propiedades` pasó de `update, delete: if request.auth != null` a SOLO DUEÑO (`request.auth.uid == resource.data.idPropietario`). Resto igual que v5. Se desplegó con la SA alkilapp-seed tras darle `roles/firebaserules.admin` (antes daba 403 "firebaserules.rulesets.list"); el GET de reglas actuales NO está en el repo (estaban solo en la API). Gotchas API: PATCH de release no acepta `rulesetName` ("Cannot find field") → es DELETE `/releases/cloud.firestore` + POST `/releases?releaseId=...` que tampoco... el POST que funcionó es el que lleva el `name` completo en el body: `POST /v1/projects/X/releases` con `{name:"projects/X/releases/cloud.firestore", rulesetName:...}`.
- **En vivo (el padre probando):** finalizó SUS 4 publicaciones reales (Cuarto g8dDra..., 1cfZrA..., 57fp6..., Ctxe...) → quedaron `estado=finalizado` en `alkilappdb` y desaparecieron del feed de MainActivity (el filtro `it.estado != "under_review" && it.estado != "finalizado"`). El feed quedó solo con los 5 listing del seed (950+). **Los 4 del padre siguen en "finalizado"** — no se resetean sin que el padre lo pida. Verificado por uiautomator: perfil muestra 4 tarjetas con chip + "Ya lo alquilé" (solo las disponibles); ficha de "Cuarto" mostraba el botón de finalizar; perfil confirmó Inmuebles publicados renderizando. FAB: en estado collapsed (peek) queda con aire sobre el sheet (bounds confirmados); el deslizamiento en sí no se puede inyectar (INJECT_EVENTS bloqueado) → revisar visualmente con un swipe del padre.
- Pendientes globales: push a GitHub + release; iOS de AlkilApp.
## Ronda 27 (2026-09-20): deep links + fix MisPublicaciones

**Deep links** � lkilapp://propiedad/{id} abre PropiedadDetalleActivity directamente; intent-filter en manifest (ndroid:exported="true" + <intent-filter> con scheme="alkilapp" host="propiedad"); bot�n compartir genera lkilapp://propiedad/{id} y abre share sheet. PropiedadDetalleActivity detecta deep link (intent.data?.lastPathSegment), carga propiedad desde Firestore por propId y llama setupUIConDatos().

**Fix MisPublicaciones detalle** � MisPublicacionesActivity.abrirDetalle() removi� EXTRA_FOTOS (base64) del intent ? evita TransactionTooLargeException; usa EXTRA_FOTOS_URL + carga por ID desde Firestore (igual que MainActivity).

**T�cnico**: version bump v1.41 (vc23), commit 94ef478, release GitHub 1.41, APK instalado en Xiaomi (serial HGR4RJCK).
