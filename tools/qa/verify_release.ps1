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

# dist/ is what gets uploaded - it must carry the bundle that was just
# verified. The comparison is by content, not by file hash: an AAB is a zip of
# nested jars and the nested jars are re-compressed on every build, so two
# builds of the same source differ in a few bytes of zip stream while
# carrying exactly the same payload.
if ((Test-Path $aab) -and (Test-Path $distAab)) {
    $cmp = & python 'tools\qa\compare_bundles.py' $aab $distAab 2>&1 | Out-String
    Check 'dist bundle matches the freshly built one' ($LASTEXITCODE -eq 0) $cmp.Trim()
    $freshHash = (Get-FileHash $aab -Algorithm SHA256).Hash
    $shippedHash = (Get-FileHash $distAab -Algorithm SHA256).Hash
    Write-Output ("    shipped bundle sha256: " + $shippedHash)
    if ($freshHash -ne $shippedHash) {
        Write-Output ("    (build sha256 differs by zip bytes only: " + $freshHash + ")")
    }
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
Check 'exactly 6 store screenshots' ($shots.Count -eq 6) "$($shots.Count) png"
$expected = @('01_home.png', '02_home_unlocked.png', '03_favourable_days.png', '04_windfall_reading.png', '05_unlock.png', '06_dream.png')
foreach ($name in $expected) {
    Check "screenshot $name" ($shots.Name -contains $name)
}

# The screenshots are drawn by a script, not captured from the app, so they do
# not follow it on their own. The render is byte-for-byte reproducible, so the
# gate draws them again into a scratch folder and compares: a PNG that does
# not match is one the current script no longer produces, and the store would
# be showing a product that has moved on.
$scratch = 'build\_screens_check'
& powershell -NoProfile -ExecutionPolicy Bypass -File 'tools\store_assets\build_screenshots.ps1' `
    -OutDir $scratch 2>&1 | Out-Null
$stale = @()
foreach ($name in $expected) {
    $fresh = Join-Path $scratch $name
    $shipped = Join-Path 'docs\store\screenshots' $name
    if (-not (Test-Path $fresh) -or -not (Test-Path $shipped)) {
        $stale += $name
        continue
    }
    $freshHash = (Get-FileHash $fresh -Algorithm SHA256).Hash
    $shippedHash = (Get-FileHash $shipped -Algorithm SHA256).Hash
    if ($freshHash -ne $shippedHash) { $stale += $name }
}
Check 'store screenshots are what the script draws today' ($stale.Count -eq 0) `
    $(if ($stale.Count -eq 0) { 'all six match' } else { "stale: " + ($stale -join ', ') })

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
    Check 'source zip: has golden dream data' ($entries -match 'golden_dream\.tsv$')
} else {
    Check 'source zip exists' $false $srcZip
}

Write-Output ''
Write-Output '=== 6. Public policy page and version control ==='

# The hosted policy page must not drift from the Markdown it is generated from.
$policyCheck = & python 'tools\docs\make_privacy_html.py' --check 2>&1 | Out-String
Check 'privacy policy page is up to date' ($LASTEXITCODE -eq 0) ($policyCheck.Trim() -split "`n" | Select-Object -First 1)

# Play rejects a listing whose copy is over its character caps, and one extra
# line of marketing text is enough to cross them. The lengths are checked here
# so that an over-long release note can never reach the Console.
$lengthCheck = & python 'tools\qa\check_listing_lengths.py' 2>&1 | Out-String
Check 'store listing copy is within Play limits' ($LASTEXITCODE -eq 0) `
    (($lengthCheck.Trim() -split "`n" | Where-Object { $_ -match 'FAIL' }) -join ' ')

# The paywall split, the one free reading, the birth-day storage promise and
# the disclaimer appear in the app, the listing, the privacy policy and the
# docs, and Play reads them side by side. Drift is caught here, not by review.
$copyCheck = & python 'tools\qa\check_copy_consistency.py' 2>&1 | Out-String
Check 'product copy agrees everywhere it is repeated' ($LASTEXITCODE -eq 0) `
    (($copyCheck.Trim() -split "`n" | Where-Object { $_ -match 'FAIL' }) -join ' ')

# The store screenshots are drawn by a script, not captured from the app, so
# they do not follow it on their own. This check makes them: every phrase the
# script draws has to exist in the app, and the app's leading sentences have
# to reach the script.
$shotsCheck = & python 'tools\qa\check_screenshot_sync.py' 2>&1 | Out-String
Check 'store screenshots still say what the app says' ($LASTEXITCODE -eq 0) `
    (($shotsCheck.Trim() -split "`n" | Where-Object { $_ -match '^\[FAIL\]' }) -join ' ')

# The signing key must never be in git, not even in a private repository.
$gitExe = 'E:\Minimax\toolchain\git\cmd\git.exe'
if (Test-Path $gitExe) {
    $tracked = & $gitExe ls-files 2>$null
    Check 'git tracks no keystore' (-not ($tracked -match 'keystore\.properties$|\.jks$')) `
        ("{0} tracked files" -f ($tracked | Measure-Object).Count)
} else {
    Write-Output '[SKIP] git not found - could not check that the keystore is untracked'
}

Write-Output ''
if ($failures.Count -eq 0) {
    Write-Output 'ALL CHECKS PASSED'
    exit 0
}
Write-Output ("FAILED: " + ($failures -join '; '))
exit 1
