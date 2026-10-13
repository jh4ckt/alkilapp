<#
    Despliegue de las Cloud Functions de AlkilApp.

    POR QUE EXISTE ESTE SCRIPT
    --------------------------
    `gcloud functions deploy --set-env-vars=...` REEMPLAZA el juego completo de
    variables de entorno: no las anade. Si se despliega pasando solo lo nuevo, se
    borran las que gcloud injectaba solo (GCLOUD_PROJECT) y la funcion deja de
    arrancar con:

        Error: process.env.GCLOUD_PROJECT is not set.

    Ese fallo se produjo desplegando esta funcion. Ademas, la clave de Resend
    solo existe dentro de la configuracion desplegada (no esta en el repo, a
    proposito), asi que hay que recuperarla de ahi.

    Este script hace siempre las dos cosas: recupera la clave de la funcion
    existente y manda SIEMPRE todas las variables.

    USO
        .\desplegar.ps1                       # ambas funciones
        .\desplegar.ps1 -SoloSoporte
        .\desplegar.ps1 -SoloInmuebles
        .\desplegar.ps1 -DryRun               # imprime el comando, no despliega

    Las dos funciones comparten index.js, asi que lo normal es desplegar ambas.
#>
[CmdletBinding()]
param(
    [switch]$SoloSoporte,
    [switch]$SoloInmuebles,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'

$PROJECT   = 'gen-lang-client-0040505884'
$REGION    = 'us-central1'
$RUNTIME   = 'nodejs22'
$SOPORTE   = 'notificarNuevoTicketSoporte'
$INMUEBLES = 'notificarNuevoInmueblePorAprobar'

# --- 1) Recuperar la clave de Resend de la funcion ya desplegada ------------
# Nunca se escribe aqui: se lee de la configuracion en la nube.
Write-Host "1) Buscando RESEND_API_KEY en la funcion $SOPORTE..." -ForegroundColor Cyan

$actual = gcloud functions describe $SOPORTE --project $PROJECT --region $REGION --format 'value(environmentVariables)' 2>$null
if (-not $actual) {
    Write-Host "   ERROR: no se pudo leer la configuracion de $SOPORTE." -ForegroundColor Red
    Write-Host "   Sin la clave de Resend no se pueden enviar los correos." -ForegroundColor Red
    exit 1
}

$clave = [regex]::Match($actual, 'RESEND_API_KEY=(re_[A-Za-z0-9_]+)').Groups[1].Value
if (-not $clave) {
    Write-Host "   ERROR: $SOPORTE no tiene RESEND_API_KEY." -ForegroundColor Red
    Write-Host "   Creala en https://resend.com -> API Keys y despues:" -ForegroundColor Yellow
    Write-Host "   gcloud functions deploy $SOPORTE ... --set-env-vars=GCLOUD_PROJECT=$PROJECT,RESEND_API_KEY=re_...,SOPORTE_DESTINO=..." -ForegroundColor Yellow
    exit 1
}
Write-Host "   Clave encontrada: $($clave.Substring(0, 6))...$($clave.Substring($clave.Length - 4))" -ForegroundColor Green

$destinoSoporte = [regex]::Match($actual, 'SOPORTE_DESTINO=([^;]+)').Groups[1].Value
if (-not $destinoSoporte) { $destinoSoporte = 'alkilapp2026@gmail.com' }

# El panel admin, para el boton "Revisar y aprobar" del correo de inmueble.
$panelUrl = 'https://alkilapp-admin-288429280561.us-central1.run.app'

# --- 2) Definir cada despliegue ---------------------------------------------
# GCLOUD_PROJECT va SIEMPRE: es la que se perdia y rompe la funcion.
$despliegues = @()

if (-not $SoloInmuebles) {
    $despliegues += @{
        Nombre  = $SOPORTE
        Trigger = "projects/$PROJECT/databases/alkilappdb/documents/soporte/{ticketId}"
        Env     = "GCLOUD_PROJECT=$PROJECT,RESEND_API_KEY=$clave,SOPORTE_DESTINO=$destinoSoporte"
    }
}

if (-not $SoloSoporte) {
    $despliegues += @{
        Nombre  = $INMUEBLES
        Trigger = "projects/$PROJECT/databases/alkilappdb/documents/propiedades/{propiedadId}"
        Env     = "GCLOUD_PROJECT=$PROJECT,RESEND_API_KEY=$clave,INMUEBLES_DESTINO=$destinoSoporte,SOPORTE_DESTINO=$destinoSoporte,PANEL_URL=$panelUrl"
    }
}

if ($despliegues.Count -eq 0) {
    Write-Host 'ERROR: no hay nada que desplegar.' -ForegroundColor Red
    exit 1
}

# --- 3) Desplegar ------------------------------------------------------------
$i = 0
foreach ($d in $despliegues) {
    $i++
    Write-Host "`n$($i)/$($despliegues.Count) Desplegando $($d.Nombre)..." -ForegroundColor Cyan

    $args = @(
        'functions', 'deploy', $d.Nombre,
        '--no-gen2',                    # el codigo usa firebase-functions/v1
        "--runtime=$RUNTIME",
        "--region=$REGION",
        "--source=.",
        "--entry-point=$($d.Nombre)",
        "--trigger-resource=$($d.Trigger)",
        '--trigger-event=providers/cloud.firestore/eventTypes/document.create',
        "--set-env-vars=$($d.Env)",
        '--quiet'
    )

    if ($DryRun) {
        Write-Host '   [DRY RUN] gcloud ' ($args -join ' ') -ForegroundColor DarkGray
        # La clave se oculta al imprimir para no ensuciar la consola.
        continue
    }

    gcloud @args
    if ($LASTEXITCODE -ne 0) {
        Write-Host "   ERROR: fallo el despliegue de $($d.Nombre)" -ForegroundColor Red
        exit 1
    }
}

Write-Host "`nListo." -ForegroundColor Green
if ($DryRun) { Write-Host '(Dry run: no se desplego nada)' -ForegroundColor DarkGray }
else {
    Write-Host 'Comprueba que llegaron los correos creando un inmueble de prueba en estado under_review.'
    Write-Host '  Ver logs:  gcloud logging read "resource.type=cloud_function" --limit 10'
}