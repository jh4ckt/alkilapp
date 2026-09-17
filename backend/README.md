# AlkilApp — Backend

Scripts auxiliares de Node.js para el proyecto **AlkilApp** sobre
Google Cloud Firestore (base **`alkilappdb`** en el proyecto
**`gen-lang-client-0040505884`**).

> La app Android lee las colecciones `usuarios` y `propiedades` en vivo. El esquema
> "nuevo" del spec (`users` / `listings` / `reviews` / `chats`) se **fusionó**
> sobre esas mismas colecciones: cada documento de `propiedades` ahora lleva los
> campos del spec (`propertyType`, `rentalType`, `features`, `location`, `geohash`,
> `photos`, `ownerId`, `viewsCount`, `status`, `createdAt`/`updatedAt`) **además**
> de los campos que ya leía la app (`titulo`, `precio`, `moneda`, `barrio`, `lat`,
> `lng`, `fotos`, …). Así el seed se muestra automáticamente en la app.

## Requisitos
- Node.js 18+ y npm.

## Configuración
```bash
cd backend
npm install
```

Credenciales (proyecto `gen-lang-client-0040505884`, base `alkilappdb`):

| Método | Cómo |
|--------|------|
| Default (recomendado) | El script usa `backend/credentials/alkilapp-seed-sa.json` (service account `alkilapp-seed`, rol `datastore.user`) si existe. |
| `GOOGLE_APPLICATION_CREDENTIALS` | Apunta la variable a cualquier key JSON con permisos de Firestore (p. ej. `roles/datastore.user`). |
| `gcloud auth application-default login` | Credenciales por defecto de Application Default Credentials. |

## Ejecutar el seed
```bash
cd backend
npm run seed
```

El script es **idempotente**: borra solo los documentos de prueba (IDs fijos con
prefijo `seed-*` y los demos viejos) antes de cargar de nuevo. No toca
publicaciones reales hechas desde la app.

## Verificación rápida
```bash
node -e "const {Firestore}=require('@google-cloud/firestore');const db=new Firestore({projectId:'gen-lang-client-0040505884',databaseId:'alkilappdb'});Promise.all([db.collection('usuarios').limit(10).get(),db.collection('propiedades').limit(10).get(),db.collection('chats').limit(5).get()]).then(([a,b,c])=>console.log({usuarios:a.size,propiedades:b.size,chats:c.size}));"
```

## Seguridad
- `backend/credentials/` y `node_modules/` están en `.gitignore`: la key de la
  service account NO debe subirse al repositorio.
- La cuenta `alkilapp-seed` solo tiene `roles/datastore.user` (lectura/escritura
  de Firestore, sin acceso a otros servicios del proyecto).