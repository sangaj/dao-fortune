# Packages the deliverable: a source zip plus the signed bundle.
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\release\package_release.ps1

$ErrorActionPreference = 'Stop'

$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$Dist = Join-Path $Root 'dist'
New-Item -ItemType Directory -Force -Path $Dist | Out-Null

$Aab = Join-Path $Root 'app\build\outputs\bundle\release\app-release.aab'
if (-not (Test-Path $Aab)) {
    Write-Output "No signed bundle found. Run: gradlew :app:bundleRelease"
    exit 1
}

Copy-Item $Aab (Join-Path $Dist 'app-release.aab') -Force
Write-Output ("bundle -> " + (Join-Path $Dist 'app-release.aab'))

$staging = Join-Path $Dist 'dao-fortune-src'
if (Test-Path $staging) {
    Remove-Item $staging -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $staging | Out-Null

$include = @(
    'app', 'docs', 'tools', 'gradle',
    'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties',
    'gradlew', 'gradlew.bat', '.gitignore', 'README.md', 'AGENTS.md'
)
foreach ($item in $include) {
    $source = Join-Path $Root $item
    if (Test-Path $source) {
        Copy-Item $source -Destination $staging -Recurse -Force
    }
}
# drop build output and local secrets from the source copy
foreach ($junk in @('build', '.gradle', 'local.properties', '__pycache__')) {
    Get-ChildItem $staging -Recurse -Directory -Filter $junk -ErrorAction SilentlyContinue |
        ForEach-Object { Remove-Item $_.FullName -Recurse -Force -ErrorAction SilentlyContinue }
}
Get-ChildItem $staging -Recurse -Include '*.pyc', '*.pyo' -File -ErrorAction SilentlyContinue |
    ForEach-Object { Remove-Item $_.FullName -Force -ErrorAction SilentlyContinue }
Get-ChildItem $staging -Recurse -Filter 'keystore.properties' -ErrorAction SilentlyContinue |
    ForEach-Object { Remove-Item $_.FullName -Force -ErrorAction SilentlyContinue }

$zip = Join-Path $Dist 'dao-fortune-source-v1.0.0.zip'
if (Test-Path $zip) { Remove-Item $zip -Force }
Compress-Archive -Path (Join-Path $staging '*') -DestinationPath $zip -Force
Write-Output ("source -> " + $zip)

Get-ChildItem $Dist -File | Select-Object Name, @{n='MB';e={[math]::Round($_.Length/1MB,2)}} |
    Format-Table -AutoSize | Out-String -Width 100 | Write-Output
