# Verificacion en vivo: bloqueo de cuentas desactivadas (v1.63.0)

Fecha: 2026-10-06. Dispositivo: Xiaomi `6phyeanrfyhmozv8` (adb).
APK: `app/build/outputs/apk/debug/app-debug.apk` (versionCode 43, versionName 1.63.0).

## Que se probo

Se creo un usuario QA dedicado y se le puso `estado = "desactivado"` (igual que lo
haria el panel admin con `POST /api/usuarios/:id/estado`), y luego se intento ingresar
con el desde la pantalla de login.

### Creacion del usuario QA (sin permisos de Firebase Auth Admin)

La service account `alkilapp-seed-sa.json` NO tiene `roles/firebaseauth.admin`, asi
que los `accounts:signUp` del Admin SDK dan `auth/insufficient-permission`. La API key
del proyecto esta restringida al paquete Android (`com.alkilapp`), por lo que un REST
sin headers da 403. Se utilizo la ruta que presenta los headers que valida esa
restriccion:

- `X-Android-Package: com.alkilapp`
- `X-Android-Cert: b4ed9dc44203e58db68eb745fc7beda902cc879f` (SHA1 debug, el registrado en Firebase)

```
POST https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=<api-key>
```

Email: `usuario.bloqueado.qa@gmail.com` / password de QA `Prueba123456`.
Despues, con la SA de Firestore: `usuarios/{uid}.estado = "desactivado"`.

### Login bloqueado: evidencia

Tras tocar "Ingresar" con las credenciales del usuario desactivado:

- El login NO entra (permanece en `AuthActivity`).
- Aparece el error inline (`tvAuthError`):
  "Tu cuenta esta desactivada. Inicia sesion con otro usuario."
- El campo de password vuelve a su placeholder: la Activity limpia el campo.
- `FirebaseAuth.currentUser` queda sin sesion: el store
  `com.google.firebase.auth.api.Store.*.xml` conserva solo 65 bytes (sin `FIREBASE_USER`),
  confirmando que `signOut()` se ejecuto.

### Restauracion de la sesion real

La sesion real (`presupuestos@oleohidraulic.com`, uid `smpJfxQT0vUMaCMalJWUej1qPnw1`)
se respaldo (archivo de auth store) antes de cerrar sesion y se restauro al finalizar.
Verificado: el store vuelve a contener `FIREBASE_USER` para ese uid.

### Limpieza

El usuario QA se elimino por completo (REST `accounts:delete` usando el `idToken` recien
obtenido, y `usuarios/{uid}` borrado con la SA). No queda ningun registro.

## Validacion adicional (sin dispositivo)

- `backend/verify-estado-cuenta.cjs` replica la normalizacion exacta de
  `EstadoCuenta.esDesactivado` (`trim` + `lowercase` contra `"desactivado"`) sobre los
  datos reales de `alkilappdb/usuarios`:
  - 1 de 19 usuarios bloqueado (`jhoner@gmail.com`, estado `"desactivado"`).
  - 0 falsos positivos en los otros 18 (todos sin campo `estado`).
  - 10/10 casos limite OK: `DESACTIVADO`, `"  desactivado  "`, `Desactivado`,
    `activo`, `suspendido`, `desactivadoTemporal`, `""`, `null`, `undefined`.
- `firestore.rules` permite al cliente LEER `usuarios/{usuario}` (`allow read: if true`),
  por lo que el chequeo del login no falla silenciosamente.
- `estado` NO esta en `camposPerfil()` (la whitelist de escritura del cliente), asi que
  un usuario desactivado no puede auto-reactivarse escribiendo `estado = "activo"`.
- El endpoint del admin (`POST /api/usuarios/:id/estado`) solo acepta
  `activo|suspendido|desactivado` y escribe exclusivamente `estado`.
  El booleano legacy `activo` no se toca.

## Pendientes ajenos a este bloqueo

- Correo de verificacion de Firebase a `jhoner.qt@gmail.com`: la cuenta ya existe
  (uid `OSj0OpPqbZZTird6S8wElprbSXW2`) y no se puede generar el enlace sin permisos de
  Auth Admin ni sin su password; confirmar la llegada al buzón es del usuario.
- `backend/firestore.rules` cambio en este release pero no esta desplegado:
  `cd backend && node reglas.js deploy firestore.rules`.