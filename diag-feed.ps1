$ErrorActionPreference = 'Stop'
$adb = "C:\Users\jh4ck\AppData\Local\Android\Sdk\platform-tools\adb.exe"

function Lineas($file, $inicio, $fin) {
    $l = Get-Content $file
    for ($i = $inicio; $i -le [Math]::Min($fin, $l.Count); $i++) {
        "    L{0}: [{1}]" -f $i, $l[$i - 1].Trim()
    }
}

"===== (1) MainActivity.kt L688-732: el bloque COMPLETO escucharPropiedades (query + filtros + distancia?) ====="
Lineas "D:\alkilapp\app\src\main\java\com\alkilapp\MainActivity.kt" 688 732

"`n===== (2) busco DISTANCIA en MainActivity: keywords clave ====="
$l = Get-Content "D:\alkilapp\app\src\main\java\com\alkilapp\MainActivity.kt"
for ($i = 0; $i -lt $l.Count; $i++) {
    if ($l[$i] -match 'distance|Distance|distancia|Distancia|km|radio|Radius|radio|latitud|longitud|latitud') {
        if ($l[$i].Trim() -match 'distance|Distance|distancia|km|radio|Radius|latitud|longitud') {
            "    L{0}: {1}" -f ($i + 1), $l[$i].Trim()
        }
    }
}

"`n===== (3) comodidades (string-array) en strings.xml hoy ====="
$path = "D:\alkilapp\app\src\main\res\values\strings.xml"
$s = [IO.File]::ReadAllText($path, [Text.Encoding]::UTF8)
$m = [regex]::Match($s, '(?s)<string-array name="comodidades">(.*?)</string-array>')
if ($m.Success) {
    $items = [regex]::Matches($m.Groups[1].Value, '<item>([^<]*)</item>') | ForEach-Object { $_.Groups[1].Value }
    "    comodidades ({0}): {1}" -f $items.Count, ($items -join ' | ')
} else {
    "    NO existe string-array 'comodidades' en strings.xml — busco en todos los res..."
    Get-ChildItem -Path D:\alkilapp\app\src\main\res -Recurse -Filter *.xml | ForEach-Object {
        $c = [IO.File]::ReadAllText($_.FullName, [Text.Encoding]::UTF8)
        $mm = [regex]::Match($c, '(?s)<string-array name="comodidades">(.*?)</string-array>')
        if ($mm.Success) {
            $its = [regex]::Matches($mm.Groups[1].Value, '<item>([^<]*)</item>') | ForEach-Object { $_.Groups[1].Value }
            "    {0} ({1}): {2}" -f $_.FullName, $its.Count, ($its -join ' | ')
        }
    }
}

"`n===== (4) array de DISTRITOS de Lima: busco TODOS los string-array con 'Lima' o similar ====="
foreach ($f in (Get-ChildItem -Path D:\alkilapp\app\src\main\res -Recurse -Filter *.xml)) {
    $c = [IO.File]::ReadAllText($f.FullName, [Text.Encoding]::UTF8)
    if ($c -match 'departamento|_Lima|Lima"|ciudad') {
        $mm = [regex]::Match($c, '(?s)<string-array name="[^"]*[Ll]ima[^"]*">(.*?)</string-array>')
        if ($mm.Success) {
            $its = [regex]::Matches($mm.Groups[1].Value, '<item>([^<]*)</item>') | ForEach-Object { $_.Groups[1].Value }
            "    {0}" -f $f.FullName
            "      nombre y items ({0}):" -f $its.Count
            $its | ForEach-Object { "        {0}" -f $_ }
        }
    }
}
