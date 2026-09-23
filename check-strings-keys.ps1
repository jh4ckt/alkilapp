$ErrorActionPreference = "Stop"
cd "D:\alkilapp"
function Faltantes {
  param($layout, $xml)
  $lRaw = Get-Content $layout -Raw
  $sRaw = Get-Content $xml -Raw
  $pedidas = [regex]::Matches($lRaw, '@string/([\w.]+)') | ForEach-Object { $_.Groups[1].Value } | Select-Object -Unique
  $defs = [regex]::Matches($sRaw, '<string\s+name="([\w.]+)"') | ForEach-Object { $_.Groups[1].Value } | Select-Object -Unique
  $falta = @()
  foreach ($p in $pedidas) {
    if ($p -notin $defs) { $falta += $p }
  }
  return ,$falta
}

$r = Faltantes "app\src\main\res\layout\activity_auth.xml" "app\src\main\res\values\strings.xml"
"===== 1) strings referenciadas por activity_auth.xml que NO existen ===== "
if ($r.Count -eq 0) { "  OK: ninguna falta" } else { "  *** FALTAN {0}: ***" -f $r.Count; foreach ($x in $r) { "    {0}" -f $x } }
"===== 2) verifico que NINGUNA clave este duplicada (para no reintroducir el error de build) ===== "
$sRaw = Get-Content "app\src\main\res\values\strings.xml" -Raw
$claves = [regex]::Matches($sRaw, '<string\s+name="([\w.]+)"') | ForEach-Object { $_.Groups[1].Value }
$dups = $claves | Group-Object | Where-Object { $_.Count -gt 1 }
if ($dups) { "  *** DUPLICADOS: ***"; foreach ($d in $dups) { "    {0} x{1}" -f $d.Name, $d.Count } } else { "  OK: 0 duplicados ({0} claves unicas)" -f ($claves | Select-Object -Unique).Count }
