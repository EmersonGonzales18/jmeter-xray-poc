param(
    [string]$JmxFile = "jmeter\test.jmx",
    [string]$OutputDir = "temp"
)

$ErrorActionPreference = "Stop"

Write-Host "Leyendo JMX: $JmxFile"

if (!(Test-Path $JmxFile)) {
    throw "No existe el archivo: $JmxFile"
}

# Limpiar temporales anteriores
if (Test-Path $OutputDir) {
    Remove-Item $OutputDir -Recurse -Force
}

New-Item -ItemType Directory -Path $OutputDir | Out-Null

[xml]$xml = Get-Content $JmxFile -Raw

# Busca Thread Groups normales de JMeter
$threadGroups = $xml.SelectNodes("//ThreadGroup")

if ($threadGroups.Count -eq 0) {
    throw "No se encontraron Thread Groups en $JmxFile"
}

Write-Host ""
Write-Host "Thread Groups encontrados: $($threadGroups.Count)"
Write-Host ""

for ($i = 0; $i -lt $threadGroups.Count; $i++) {

    $selectedName = $threadGroups[$i].GetAttribute("testname")

    if ([string]::IsNullOrWhiteSpace($selectedName)) {
        $selectedName = "ThreadGroup-$($i + 1)"
    }

    # Nombre seguro para archivos/carpetas
    $safeName = $selectedName -replace '[<>:"/\\|?*]', '-'
    $safeName = $safeName -replace '\s+', '-'

    Write-Host "Preparando: $selectedName"

    # Cargar nuevamente el JMX original
    [xml]$copy = Get-Content $JmxFile -Raw

    $copyThreadGroups = $copy.SelectNodes("//ThreadGroup")

    for ($j = 0; $j -lt $copyThreadGroups.Count; $j++) {

        if ($j -eq $i) {
            $copyThreadGroups[$j].SetAttribute("enabled", "true")
        }
        else {
            $copyThreadGroups[$j].SetAttribute("enabled", "false")
        }
    }

    $outputFile = Join-Path $OutputDir "$safeName.jmx"

    $copy.Save($outputFile)

    Write-Host "  -> $outputFile"
}

Write-Host ""
Write-Host "Archivos JMX generados correctamente."