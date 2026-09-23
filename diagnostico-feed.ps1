$ErrorActionPreference = 'Continue'
$enc = New-Object Text.UTF8Encoding($false)
$adb = 'C:\Users\jh4ck\AppData\Local\Android\Sdk\platform-tools\adb.exe'

"===== (1) Propiedad.kt: mapeo de campos (data class + desde()) ====="
$f = 'D:\alkilapp\app\src\main\java\com\alkilapp\Propiedad.kt'
if (Test-Path -LiteralPath $f) {
    $l = Get-Content -LiteralPath $f
    for ($i = 0; $i -lt $l.Count; $i++) {
        if ($l[$i] -match 'data class|val |static|fun desde|companion|getString|toString|DocumentSnapshot|toDate\(') {
            "    L{0}: {1}" -f ($i + 1), $l[$i].Trim()
        }
    }
} else { "    NO existe Propiedad.kt en {0}" -f $f }

"`n===== (2) censo string-array 'comodidades' + 'ciudades' de LIMA en strings.xml ====="
$path = 'D:\alkilapp\app\src\main\res\values\strings.xml'
if (Test-Path -LiteralPath $path) {
    $s = [IO.File]::ReadAllText($path, [Text.Encoding]::UTF8)
    foreach ($name in 'comodidades', 'ciudades_Lima', 'distritos_Lima', 'ciudades_LIMA', 'zonaDistritos_Lima') {
        $m = [regex]::Match($s, '(?s)<string-array name="' + [regex]::Escape($name) + '">(.*?)</string-array>')
        if ($m.Success) {
            $its = [regex]::Matches($m.Groups[1].Value, '<item>([^<]*)</item>') | ForEach-Object { $_.Groups[1].Value }
            "    {0} ({1} items): {2}" -f $name, $its.Count, ($its -join ' | ')
        } else {
            "    (no existe array '{0}')" -f $name
        }
    }
} else { "    NO existe strings.xml en {0}" -f $path }

"`n===== (3) dump de pantalla: feed real AHORA (ver si el 'Inmuebles Cercanos' respeta lives) ====="
& $adb shell uiautomator dump /sdcard/feed_dump.xml 2>$null | Out-Null
$t = (& $adb shell cat /sdcard/feed_dump.xml 2>$null) -join ''
$mm = [regex]::Matches($t, 'text="([^"]{1,60})"')
"    textos visibles ({0}):" -f $mm.Count
$mm | ForEach-Object { "      [{0}]" -f $_.Groups[1].Value } | Select-Object -First 40
