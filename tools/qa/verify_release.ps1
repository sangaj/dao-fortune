# Runs every check that can be done on this machine, in one command.
#
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\qa\verify_release.ps1
#
# Layer 1 of docs\TESTING.md. It does not need a device and it does not touch
# Play. It is the gate that has to be green before an upload.

$ErrorActionPreference = 'Continue'

$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $Root

$env:JAVA_HOME = 'E:\Minimax\toolchain\jdk17'
$env:ANDROID_HOME = 'E:\Minimax\toolchain\android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = 'E:\Minimax\toolchain\gradle-home'

$failures = New-Object System.Collections.Generic.List[string]
function Check($name, $ok, $detail) {
    $mark = if ($ok) { 'PASS' } else { 'FAIL' }
    Write-Output ("[{0}] {1}{2}" -f $mark, $name, $(if ($detail) { " - $detail" } else { '' }))
    if (-not $ok) { $failures.Add($name) }
}

Write-Output '=== 1. Python reference self-check ==='
$py = & python 'tools\reference\luopan_reference.py' 2>&1 | Out-String
Check 'python reference self-check' ($py -match 'SELF-CHECK: ALL PASS') 'tools/reference/luopan_reference.py'

Write-Output ''
Write-Output '=== 2. Unit tests + lint + signed bundle ==='
# --no-build-cache: moving resource files between folders leaves the cached
# merge result stale, and AAPT then fails with "resource mipmap/... not found".
# A release gate has to be deterministic, so it does not trust the cache.
& .\gradlew.bat :app:testDebugUnitTest :app:lintRelease :app:bundleRelease --no-build-cache --console=plain 2>&1 |
    Out-File -FilePath 'build\_verify_release.log' -Encoding utf8
$gradleExit = $LASTEXITCODE
Check 'gradle build' ($gradleExit -eq 0) "exit=$gradleExit (log: build\_verify_release.log)"

$results = Get-ChildItem 'app\build\test-results\testDebugUnitTest' -Filter '*.xml' -ErrorAction SilentlyContinue
$total = 0; $bad = 0
foreach ($file in $results) {
    [xml]$xml = Get-Content $file.FullName
    $total += [int]$xml.testsuite.tests
    $bad += [int]$xml.testsuite.failures + [int]$xml.testsuite.errors
}
Check 'unit tests' ($total -gt 0 -and $bad -eq 0) "$total tests, $bad failures"

$lintTxt = 'app\build\reports\lint-results-release.txt'
if (Test-Path $lintTxt) {
    $lint = Get-Content $lintTxt -Raw -Encoding UTF8
    $errors = ([regex]::Matches($lint, ': Error: ')).Count
    Check 'lint has no errors' ($errors -eq 0) "$errors error(s) in $lintTxt"
} else {
    Check 'lint report exists' $false $lintTxt
}

Write-Output ''
Write-Output '=== 3. Bundle validation (bundletool) ==='
$aab = 'app\build\outputs\bundle\release\app-release.aab'
$distAab = 'dist\app-release.aab'
if (Test-Path $aab) {
    $cp = & python 'tools\qa\bundletool_cp.py' 2>$null | Out-String
    $cp = $cp.Trim()
    $btOut = & 'E:\Minimax\toolchain\jdk17\bin\java.exe' -cp $cp `
        com.android.tools.build.bundletool.BundleToolMain validate --bundle=$aab 2>&1 | Out-String
    Check 'bundletool validate' ($LASTEXITCODE -eq 0) "exit=$LASTEXITCODE"

    $mf = & 'E:\Minimax\toolchain\jdk17\bin\java.exe' -cp $cp `
        com.android.tools.build.bundletool.BundleToolMain dump manifest --bundle=$aab --module=base 2>&1 | Out-String
    Check 'manifest package id'   ($mf -match 'package="com\.laki\.daofortune"')
    Check 'manifest minSdk 26'    ($mf -match 'minSdkVersion="26"')
    Check 'manifest targetSdk 36' ($mf -match 'targetSdkVersion="36"')
    Check 'no location permission' (-not ($mf -match 'ACCESS_(FINE|COARSE|BACKGROUND)_LOCATION'))
    Check 'versionCode present'   ($mf -match 'versionCode="\d+"')

    # 16 KB page sizes: the bundle does carry native code (androidx.graphics.path),
    # so the alignment of its PT_LOAD segments has to be checked, not assumed.
    $alignOut = & python 'tools\qa\check_16kb_pages.py' $aab 2>&1 | Out-String
    Check 'native libraries are 16 KB aligned' ($LASTEXITCODE -eq 0) `
        (($alignOut -split "`n" | Where-Object { $_ -match 'PASSED|FAILED' }) -join ' ')
} else {
    Check 'fresh bundle exists' $false $aab
}

# dist/ is what gets uploaded - it must be the bundle that was just verified.
if ((Test-Path $aab) -and (Test-Path $distAab)) {
    $fresh = (Get-FileHash $aab -Algorithm SHA256).Hash
    $shipped = (Get-FileHash $distAab -Algorithm SHA256).Hash
    Check 'dist bundle matches the freshly built one' ($fresh -eq $shipped) `
        $(if ($fresh -eq $shipped) { $fresh.Substring(0, 16) } else { 'run package_release.ps1' })
} else {
    Check 'dist bundle exists' $false $distAab
}

Write-Output ''
Write-Output '=== 4. Signature ==='
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path $aab).Path)
$names = $zip.Entries | ForEach-Object { $_.FullName }
$zip.Dispose()
Check 'META-INF/DAO-FORT.SF'  ($names -contains 'META-INF/DAO-FORT.SF')
Check 'META-INF/DAO-FORT.RSA' ($names -contains 'META-INF/DAO-FORT.RSA')
Check 'META-INF/MANIFEST.MF'  ($names -contains 'META-INF/MANIFEST.MF')

Write-Output ''
Write-Output '=== 5. Store assets and source package ==='
$shots = Get-ChildItem 'docs\store\screenshots' -Filter '*.png' -File -ErrorAction SilentlyContinue
Check 'exactly 5 store screenshots' ($shots.Count -eq 5) "$($shots.Count) png"
$expected = @('01_home.png', '02_home_unlocked.png', '03_favourable_days.png', '04_windfall_reading.png', '05_unlock.png')
foreach ($name in $expected) {
    Check "screenshot $name" ($shots.Name -contains $name)
}

# The year sign must never be able to leave the device, so its preferences
# file must stay out of the backup allow-list. The rules are parsed as XML:
# a comment that names the file is fine, an <include> that lists it is not.
$backupPaths = @()
foreach ($rulesFile in @('app\src\main\res\xml\backup_rules.xml', 'app\src\main\res\xml\data_extraction_rules.xml')) {
    [xml]$rulesDoc = Get-Content $rulesFile -Raw -Encoding UTF8
    $backupPaths += $rulesDoc.SelectNodes('//include') | ForEach-Object { $_.GetAttribute('path') }
}
$profileBackedUp = ($backupPaths | Where-Object { $_ -like '*dao_fortune_profile*' }).Count -gt 0
Check 'year sign excluded from backup' (-not $profileBackedUp) `
    ("backed up: " + (($backupPaths | Select-Object -Unique) -join ', '))

$srcZip = 'dist\dao-fortune-source-v1.0.0.zip'
if (Test-Path $srcZip) {
    $zip2 = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path $srcZip).Path)
    $entries = $zip2.Entries | ForEach-Object { $_.FullName }
    $zip2.Dispose()
    Check 'source zip: no keystore.properties' (-not ($entries -match 'keystore\.properties$'))
    Check 'source zip: no .jks'               (-not ($entries -match '\.jks$'))
    Check 'source zip: no __pycache__'        (-not ($entries -match '__pycache__'))
    Check 'source zip: has golden data'       ($entries -match 'golden_pillars\.tsv$')
    Check 'source zip: has golden natal data' ($entries -match 'golden_natal\.tsv$')
} else {
    Check 'source zip exists' $false $srcZip
}

Write-Output ''
if ($failures.Count -eq 0) {
    Write-Output 'ALL CHECKS PASSED'
    exit 0
}
Write-Output ("FAILED: " + ($failures -join '; '))
exit 1
