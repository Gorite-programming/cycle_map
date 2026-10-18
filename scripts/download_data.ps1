# ==============================================================================
# CycleMap Dataset Download & Setup Script (Windows PowerShell)
#
# Target datasets:
#   1. Boundary GeoJSON polygons (boundaries/ -> sandbox/data-tool/data/raw/boundaries/)
#   2. MLIT Medical facilities (P04-20 2020 v3.0: 5 prefectures)
#   3. MLIT School facilities (P29-21 2021 v2.0: 5 prefectures)
# ==============================================================================

$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " CycleMap Dataset Download & Setup Script" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Setup boundary GeoJSON for sandbox/data-tool
$sandboxBoundaries = "sandbox/data-tool/data/raw/boundaries"
New-Item -ItemType Directory -Force -Path $sandboxBoundaries | Out-Null
if (Test-Path "boundaries/*.geojson") {
    Copy-Item "boundaries/*.geojson" -Destination $sandboxBoundaries -Force
    Write-Host "[OK] Copied boundary GeoJSON files to $sandboxBoundaries" -ForegroundColor Green
} else {
    Write-Warning "boundaries/*.geojson not found."
}

# Prefectures list
$prefs = @(
    @{ Code = "31"; Name = "Tottori" },
    @{ Code = "32"; Name = "Shimane" },
    @{ Code = "33"; Name = "Okayama" },
    @{ Code = "34"; Name = "Hiroshima" },
    @{ Code = "35"; Name = "Yamaguchi" }
)

$tempDir = "data_temp"
New-Item -ItemType Directory -Force -Path $tempDir | Out-Null

try {
    # 2. Medical facilities (P04-20 v3.0)
    $medBaseDir = "sandbox/data-tool/data/raw/kokudo/medical"
    New-Item -ItemType Directory -Force -Path $medBaseDir | Out-Null
    Write-Host "`n--- [1/2] MLIT Medical Data (P04-20 v3.0) ---" -ForegroundColor Yellow

    foreach ($p in $prefs) {
        $code = $p.Code
        $name = $p.Name
        $zipName = "P04-20_${code}_GML.zip"
        $url = "https://nlftp.mlit.go.jp/ksj/gml/data/P04/P04-20/$zipName"
        $zipPath = Join-Path $tempDir $zipName
        $destDir = Join-Path $medBaseDir "P04-20_${code}_GML"
        $targetGeojson = Join-Path $destDir "P04-20_${code}.geojson"

        if (-not (Test-Path $targetGeojson)) {
            Write-Host "[$name ($code)] Downloading: $url"
            curl.exe -s -L -o $zipPath $url
            Expand-Archive -Path $zipPath -DestinationPath $medBaseDir -Force
            Remove-Item -Force $zipPath
            Write-Host "  -> Extracted: $targetGeojson" -ForegroundColor Green
        } else {
            Write-Host "[$name ($code)] Already exists: $targetGeojson" -ForegroundColor DarkGray
        }
    }

    # 3. School facilities (P29-21 v2.0)
    $schoolBaseDir = "sandbox/data-tool/data/raw/kokudo/school"
    New-Item -ItemType Directory -Force -Path $schoolBaseDir | Out-Null
    Write-Host "`n--- [2/2] MLIT School Data (P29-21 v2.0) ---" -ForegroundColor Yellow

    foreach ($p in $prefs) {
        $code = $p.Code
        $name = $p.Name
        $zipName = "P29-21_${code}_GML.zip"
        $url = "https://nlftp.mlit.go.jp/ksj/gml/data/P29/P29-21/$zipName"
        $zipPath = Join-Path $tempDir $zipName
        $destDir = Join-Path $schoolBaseDir "P29-21_${code}_GML"
        $targetGeojson = Join-Path $destDir "P29-21_${code}.geojson"

        if (-not (Test-Path $targetGeojson)) {
            Write-Host "[$name ($code)] Downloading: $url"
            curl.exe -s -L -o $zipPath $url
            New-Item -ItemType Directory -Force -Path $destDir | Out-Null
            Expand-Archive -Path $zipPath -DestinationPath $destDir -Force
            Remove-Item -Force $zipPath
            Write-Host "  -> Extracted: $targetGeojson" -ForegroundColor Green
        } else {
            Write-Host "[$name ($code)] Already exists: $targetGeojson" -ForegroundColor DarkGray
        }
    }
}
finally {
    if (Test-Path $tempDir) {
        Remove-Item -Recurse -Force $tempDir
    }
}

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host " [SUCCESS] All external datasets downloaded and placed!" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
