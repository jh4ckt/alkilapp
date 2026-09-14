# AlkilApp

Aplicación Android de geolocalización e inmuebles en tiempo real, integrada con
**Google Maps Platform** y **Fused Location Provider API**.

## Funcionalidades (v1)

- Mapa de Google a pantalla completa (Google Maps SDK for Android).
- Marcador con la ubicación actual del usuario (Fused Location Provider) y botones de zoom/ubicación.
- Barra de búsqueda flotante que filtra la lista de departamentos.
- Panel inferior deslizable (BottomSheet) con la lista de inmuebles; tocar uno lo muestra en el mapa.

## Requisitos previos

1. Crear un proyecto en [Google Cloud Console](https://console.cloud.google.com/) (ej. `AlkilApp`).
2. Habilitar las APIs: **Maps SDK for Android**, **Places API** y **Geocoding API**.
3. Crear una **Clave de API** y restringirla por aplicación Android:
   - Paquete: `com.alkilapp`
   - Huella SHA-1 del certificado de depuración/firma (ver más abajo).

## Cómo obtener el SHA-1 (debug)

```bat
keytool -list -v -keystore %USERPROFILE%\.android\debug.keystore -alias androiddebugkey -storepass android -keypass android
```

## Configuración del proyecto

La clave NO se sube al repositorio. Se toma de `local.properties` (que no está versionado):

```
sdk.dir=C\:\\Users\\jh4ck\\AppData\\Local\\Android\\Sdk
MAPS_API_KEY=TU_API_KEY_REAL_AQUI
```

La clave se inyecta al build mediante el placeholder `${MAPS_API_KEY}` en `AndroidManifest.xml`.

## Compilar

```bat
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
call gradlew.bat assembleDebug
```

El APK queda en `app\build\outputs\apk\debug\app-debug.apk`.

## Estructura

```
app/src/main/java/com/alkilapp/
├── MainActivity.kt            → mapa, ubicación, permisos, cámara
├── data/Departamento.kt       → modelo + datos de ejemplo (CABA)
└── ui/DepartamentoAdapter.kt  → lista del BottomSheet + filtro de búsqueda
```

## Próximos pasos

- Conexión a Places API (Nearby Search / autocompletado real).
- Ubicación continua (`requestLocationUpdates` / `getCurrentLocation`) para "tiempo real".
- Fuente de datos real (backend o JSON remoto) en lugar de los departamentos de ejemplo.