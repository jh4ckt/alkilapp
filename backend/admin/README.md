# Panel de administracion AlkilApp

Panel web para revisar y aprobar **verificaciones de identidad**, **publicaciones**,
**denuncias** y **usuarios** de la base `alkilappdb`.

Sin dependencias extra: solo el modulo `http` de Node y `@google-cloud/firestore`
(ya instalado en `backend/node_modules`).

## Local

```powershell
$env:ADMIN_PASSWORD = "tu-password"
$env:PORT = "8099"
node admin/server.js
# -> http://localhost:8099
```

Variables:

| Variable | Descripcion |
| --- | --- |
| `ADMIN_PASSWORD` | Obligatoria. Password de acceso al panel. |
| `ADMIN_SECRET` | Opcional. Firma la cookie de sesion (usa algo aleatorio en produccion). |
| `PORT` | Puerto (Cloud Run lo inyecta). |
| `GOOGLE_APPLICATION_CREDENTIALS` | Ruta al JSON de la service account. Si no existe, usa las credenciales por defecto del entorno (ADC). |

## Que hace

- **Resumen**: contadores de inmuebles, publicaciones por aprobar, verificaciones, denuncias y usuarios.
- **Verificaciones**: lista `verificaciones/{uid}` con la foto del documento; **Aprobar** marca
  `usuarios/{uid}.verification.identityVerified = true`, `verificationBadge = true` y sube
  `trustLevel` a `verified` (si era `nuevo`/`basic`). **Rechazar** guarda el motivo y el estado.
- **Publicaciones**: aprueba las que estan en `under_review` (las pasa a **`publicado`**, que la
  app lee como `disponible` en el feed y habilita el chat y el seguimiento). Ademas hay un selector
  **Cambiar** para setear el estado a mano (`publicado`/`disponible`/`finalizado`/`under_review`/
  `pendiente`) — por ejemplo para reactivar una publicacion o marcarla como finalizada.
  Tambien **destacar** con la cantidad de dias pedida (7/15/30, ver mas abajo) o quitar el destacado,
  finalizar y **eliminar** (con confirmacion).
  Las publicaciones destacadas muestran cuantos dias faltan y la fecha de vencimiento.
- **Denuncias**: lista `reports` y las marca como `resuelto`.
- **Usuarios**: nombre, tipo, estado de identidad, nivel de confianza y rating.

## Destacados: duracion y caducidad automatica

- En el app, el propietario elige **7, 15 o 30 dias** al solicitar el destaque; eso se guarda en
  `propiedades/{id}.destacadoDias` junto con `solicitudDestacar = true`.
- Al aprobar (desde el panel o con `npm run destacar -- --id <id> [--dias N]`), se escribe
  `isFeatured`, `featuredUntil = hoy + destacadoDias`, `destacadoDiasRestantes` y
  `destacadoEstado = 'aprobado'`. Si no se pasa `--dias`, se respeta lo que pidio el usuario.
- `backend/expirar.js` es el barrido que **apaga los destacados vencidos**
  (`isFeatured = false`, `destacadoEstado = 'expirado'`, `destacadoDiasRestantes = 0`) y
  actualiza el contador de los vigentes. Es idempotente.
  - Manual: `npm run expirar`
  - En la nube: `GET /cron/expirar?clave=<ADMIN_SECRET>` (sin sesion, protegido por la clave).
    Lo llama Cloud Scheduler todos los dias a las 3am (hora de Lima).
- La app tambien ignora un destacado vencido por su cuenta (`Propiedad.esDestacado`), asi que un
  barrido atrasado nunca muestra un borde dorado de mas.

## Desplegar en la nube (Cloud Run)

Requiere `gcloud` autenticado con tu cuenta (`gcloud auth login`).

```bash
gcloud config set project gen-lang-client-0040505884

# 1) Habilitar APIs (una sola vez)
gcloud services enable run.googleapis.com cloudbuild.googleapis.com artifactregistry.googleapis.com

# 2) Cuenta de servicio del panel (una sola vez)
gcloud iam service-accounts create alkilapp-admin \
  --display-name "AlkilApp Admin Panel"
gcloud projects add-iam-policy-binding gen-lang-client-0040505884 \
  --member "serviceAccount:alkilapp-admin@gen-lang-client-0040505884.iam.gserviceaccount.com" \
  --role "roles/datastore.user"

# 3) Desplegar (desde backend/)
gcloud run deploy alkilapp-admin \
  --source . \
  --region us-central1 \
  --service-account alkilapp-admin@gen-lang-client-0040505884.iam.gserviceaccount.com \
  --set-env-vars ADMIN_PASSWORD='CAMBIA_ESTO',ADMIN_SECRET='CAMBIA_ESTO_TAMBIEN' \
  --allow-unauthenticated
```

`--allow-unauthenticated` deja la URL publica pero protegida por `ADMIN_PASSWORD`.
Si preferis que ni siquiera se pueda abrir sin autenticacion de Google, quite ese flag y
usa `gcloud run services proxy alkilapp-admin --region us-central1` o IAM (`roles/run.invoker`).

La service account **no** necesita la clave JSON en la nube: el panel detecta que el archivo
no existe y usa las credenciales por defecto del servicio (ADC).

### Cloud Scheduler (caducidad de destacados)

Una sola vez, para que el barrido corra todos los dias:

```bash
gcloud services enable cloudscheduler.googleapis.com

gcloud scheduler jobs create http alkilapp-expirar-destacados \
  --location=us-central1 \
  --schedule="0 3 * * *" \
  --time-zone="America/Lima" \
  --uri="https://alkilapp-admin-288429280561.us-central1.run.app/cron/expirar?clave=<ADMIN_SECRET>" \
  --http-method=GET
```

Para verificar que dispara: `gcloud scheduler jobs run alkilapp-expirar-destacados --location=us-central1`
y despues mirar los logs de Cloud Run (debe aparecer un request con user-agent `Google-Cloud-Scheduler`).

## Notas

- `.dockerignore` excluye `credentials/`, asi que el JSON del service account nunca se sube ni
  se hornea en la imagen.
- El servicio de Firestore ya esta en modo "sin restricciones" para el SA (`roles/datastore.user`
  cubre la base `alkilappdb`).
- La recompensa por **destacar** publicaciones ya queda registrada en `destacar.js`; cuando se
  active Google Play Billing, el panel puede seguir usando el mismo boton manual.
