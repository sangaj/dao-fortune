# Renders the Play Store phone screenshots from the real engine output.
#
# Input : docs\store\reading_sample.json  (written by the SampleReadingDump test)
# Output: docs\store\screenshots\*.png
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\store_assets\build_screenshots.ps1

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$OutDir = Join-Path $Root 'docs\store\screenshots'
$SamplePath = Join-Path $Root 'docs\store\reading_sample.json'
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
if (-not (Test-Path $SamplePath)) {
    Write-Output "missing reading_sample.json - run the unit tests first"
    exit 1
}

$sample = Get-Content -Path $SamplePath -Raw -Encoding UTF8 | ConvertFrom-Json
$plate = $sample.plate

$Ink       = [System.Drawing.Color]::FromArgb(255, 11, 14, 19)
$InkHigh   = [System.Drawing.Color]::FromArgb(255, 20, 25, 34)
$Card      = [System.Drawing.Color]::FromArgb(255, 20, 25, 34)
$CardHigh  = [System.Drawing.Color]::FromArgb(255, 28, 34, 45)
$Gold      = [System.Drawing.Color]::FromArgb(255, 217, 179, 107)
$GoldSoft  = [System.Drawing.Color]::FromArgb(255, 142, 116, 66)
$Jade      = [System.Drawing.Color]::FromArgb(255, 111, 191, 160)
$Crimson   = [System.Drawing.Color]::FromArgb(255, 210, 112, 95)
$Amber     = [System.Drawing.Color]::FromArgb(255, 224, 164, 88)
$Parchment = [System.Drawing.Color]::FromArgb(255, 236, 231, 220)
$Muted     = [System.Drawing.Color]::FromArgb(255, 154, 163, 175)
$Outline   = [System.Drawing.Color]::FromArgb(255, 35, 42, 53)

$Dot = [char]0x00B7
$Dash = [char]0x2014

$Mountains = @('Zi','Gui','Chou','Gen','Yin','Jia','Mao','Yi','Chen','Xun','Si','Bing',
               'Wu','Ding','Wei','Kun','Shen','Geng','You','Xin','Xu','Qian','Hai','Ren')

$W = 1080
$H = 1920
$Margin = 56

function New-Canvas {
    $bmp = New-Object System.Drawing.Bitmap($W, $H)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    $grad = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        (New-Object System.Drawing.Point(0, 0)), (New-Object System.Drawing.Point(0, $H)),
        $InkHigh, $Ink)
    $g.FillRectangle($grad, 0, 0, $W, $H)
    $grad.Dispose()
    return @($bmp, $g)
}

function Save-Png($bmp, $g, [string]$name) {
    $g.Dispose()
    $path = Join-Path $OutDir $name
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Output ("wrote " + $path)
}

function F([string]$family, [float]$size, [System.Drawing.FontStyle]$style = [System.Drawing.FontStyle]::Regular) {
    return New-Object System.Drawing.Font($family, $size, $style, [System.Drawing.GraphicsUnit]::Pixel)
}

function Brush([System.Drawing.Color]$c) { return New-Object System.Drawing.SolidBrush($c) }

function RoundRect($g, [single]$x, [single]$y, [single]$w, [single]$h, [single]$r, [System.Drawing.Color]$fill, [System.Drawing.Color]$border) {
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $r * 2
    $path.AddArc($x, $y, $d, $d, 180, 90)
    $path.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $path.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90)
    $path.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
    $path.CloseFigure()
    $b = Brush $fill
    $g.FillPath($b, $path)
    $b.Dispose()
    if ($border.A -gt 0) {
        $p = New-Object System.Drawing.Pen($border, 2)
        $g.DrawPath($p, $path)
        $p.Dispose()
    }
    $path.Dispose()
}

function Wrap($g, [string]$text, $font, $brush, [single]$x, [single]$y, [single]$w) {
    $fmt = New-Object System.Drawing.StringFormat
    $fmt.Trimming = [System.Drawing.StringTrimming]::EllipsisCharacter
    $size = $g.MeasureString($text, $font, $w, $fmt)
    $rect = New-Object System.Drawing.RectangleF($x, $y, $w, ($size.Height + 8))
    $g.DrawString($text, $font, $brush, $rect, $fmt)
    $fmt.Dispose()
    return $size.Height
}

function Wealth-Bearings([string]$element) {
    # The four mountains ruled by each element. Water sits on the north side,
    # fire on the south, metal on the west and wood on the east; earth takes
    # the four corners, because the earth is everywhere.
    switch ($element) {
        'Wood'  { return @(60, 75, 90, 105) }
        'Fire'  { return @(150, 165, 180, 195) }
        'Metal' { return @(240, 255, 270, 285) }
        'Water' { return @(330, 345, 0, 15) }
        default { return @(30, 120, 210, 300) }
    }
}

function Draw-Dial($g, [single]$cx, [single]$cy, [single]$r, [string]$sunPalace, [string]$wealthElement, [string]$natalPalace) {
    $outer = New-Object System.Drawing.Pen($Gold, ($r * 0.026))
    $inner = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(110, 217, 179, 107), ($r * 0.011))
    $hair  = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(45, 217, 179, 107), ($r * 0.007))
    $g.DrawEllipse($outer, ($cx - $r), ($cy - $r), ($r * 2), ($r * 2))
    $g.DrawEllipse($hair, ($cx - $r * 0.80), ($cy - $r * 0.80), ($r * 1.60), ($r * 1.60))
    $g.DrawEllipse($hair, ($cx - $r * 0.30), ($cy - $r * 0.30), ($r * 0.60), ($r * 0.60))

    for ($i = 0; $i -lt 24; $i++) {
        $deg = $i * 15.0
        $a = ($deg - 90) * [Math]::PI / 180.0
        $major = ($i % 6 -eq 0)
        $len = if ($major) { $r * 0.12 } else { $r * 0.06 }
        $x1 = $cx + ($r - $r * 0.02) * [Math]::Cos($a)
        $y1 = $cy + ($r - $r * 0.02) * [Math]::Sin($a)
        $x2 = $cx + ($r - $r * 0.02 - $len) * [Math]::Cos($a)
        $y2 = $cy + ($r - $r * 0.02 - $len) * [Math]::Sin($a)
        $pen = if ($major) { $outer } else { $inner }
        $g.DrawLine($pen, $x1, $y1, $x2, $y2)
    }

    # wealth sector: one fifteen-degree arc per mountain of the wealth element.
    # The mountains of one element either sit together or take the four corners,
    # so they are drawn one by one rather than as one sweep between the
    # smallest and the largest bearing.
    $sector = New-Object System.Drawing.Pen($Jade, ($r * 0.032))
    foreach ($wb in (Wealth-Bearings $wealthElement)) {
        $g.DrawArc($sector, ($cx - $r * 0.86), ($cy - $r * 0.86), ($r * 1.72), ($r * 1.72), ($wb - 7.5 - 90), 15.0)
    }

    # the reader's own year branch: a hollow ring, never confused with the sun
    if ($natalPalace) {
        $natalIndex = [Array]::IndexOf($Mountains, $natalPalace)
        if ($natalIndex -ge 0) {
            $na = ($natalIndex * 15.0 - 90) * [Math]::PI / 180.0
            $nx = $cx + ($r + 16) * [Math]::Cos($na)
            $ny = $cy + ($r + 16) * [Math]::Sin($na)
            $ring = New-Object System.Drawing.Pen($Parchment, ($r * 0.016))
            $g.DrawEllipse($ring, ($nx - 11), ($ny - 11), 22, 22)
            $ring.Dispose()
        }
    }

    # sun marker
    $sunIndex = [Array]::IndexOf($Mountains, $sunPalace)
    if ($sunIndex -ge 0) {
        $a = ($sunIndex * 15.0 - 90) * [Math]::PI / 180.0
        $sx = $cx + ($r + 16) * [Math]::Cos($a)
        $sy = $cy + ($r + 16) * [Math]::Sin($a)
        $goldBrush = Brush $Gold
        $pts = @(
            (New-Object System.Drawing.PointF($sx, ($sy - 9))),
            (New-Object System.Drawing.PointF(($sx + 7), $sy)),
            (New-Object System.Drawing.PointF($sx, ($sy + 9))),
            (New-Object System.Drawing.PointF(($sx - 7), $sy))
        )
        $g.FillPolygon($goldBrush, $pts)
        $goldBrush.Dispose()
    }

    # needle
    $goldBrush = Brush $Gold
    $jadeBrush = Brush $Jade
    $tipY = $cy - $r * 0.60
    $tailY = $cy + $r * 0.56
    $halfW = $r * 0.058
    $pTip = New-Object System.Drawing.PointF($cx, $tipY)
    $pRight = New-Object System.Drawing.PointF(($cx + $halfW), $cy)
    $pTail = New-Object System.Drawing.PointF($cx, $tailY)
    $pLeft = New-Object System.Drawing.PointF(($cx - $halfW), $cy)
    $pLow = New-Object System.Drawing.PointF($cx, ($cy + $r * 0.14))
    $g.FillPolygon($goldBrush, @($pTip, $pRight, $pTail, $pLeft))
    $g.FillPolygon($jadeBrush, @($pTail, $pRight, $pLow, $pLeft))
    $goldBrush.Dispose(); $jadeBrush.Dispose()

    $font = F 'Segoe UI' ($r * 0.062)
    $brush = Brush ([System.Drawing.Color]::FromArgb(185, 154, 163, 175))
    $fmt = New-Object System.Drawing.StringFormat
    $fmt.Alignment = [System.Drawing.StringAlignment]::Center
    $fmt.LineAlignment = [System.Drawing.StringAlignment]::Center
    for ($i = 0; $i -lt 24; $i++) {
        $deg = $i * 15.0
        $a = ($deg - 90) * [Math]::PI / 180.0
        $lr = $r * 0.765
        $x = $cx + $lr * [Math]::Cos($a)
        $y = $cy + $lr * [Math]::Sin($a)
        $g.DrawString($Mountains[$i], $font, $brush, $x, $y, $fmt)
    }
    $font.Dispose(); $brush.Dispose(); $fmt.Dispose()
    $outer.Dispose(); $inner.Dispose(); $hair.Dispose(); $sector.Dispose()
}

function Draw-Gauge($g, [single]$cx, [single]$cy, [single]$r, [int]$score, [System.Drawing.Color]$accent) {
    $track = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(45, $accent), ($r * 0.11))
    $live = New-Object System.Drawing.Pen($accent, ($r * 0.11))
    $box = New-Object System.Drawing.RectangleF(($cx - $r), ($cy - $r), ($r * 2), ($r * 2))
    $g.DrawArc($track, $box, 135, 270)
    $g.DrawArc($live, $box, 135, (270 * ($score / 100.0)))
    $track.Dispose(); $live.Dispose()
}

function Verdict-Color([string]$verdict) {
    switch ($verdict) {
        'Exceptional' { return $Gold }
        'Favorable'   { return $Jade }
        'Mildly Favorable' { return $Jade }
        'Mixed'       { return $Amber }
        'Cautious'    { return $Amber }
        default       { return $Crimson }
    }
}

function Draw-Chevron($g, [single]$cx, [single]$cy, [single]$size, [System.Drawing.Color]$color, [bool]$left) {
    $pen = New-Object System.Drawing.Pen($color, 4)
    if ($left) {
        $g.DrawLines($pen, @(
            (New-Object System.Drawing.PointF(($cx + $size * 0.4), ($cy - $size))),
            (New-Object System.Drawing.PointF(($cx - $size * 0.3), $cy)),
            (New-Object System.Drawing.PointF(($cx + $size * 0.4), ($cy + $size)))
        ))
    } else {
        $g.DrawLines($pen, @(
            (New-Object System.Drawing.PointF(($cx - $size * 0.4), ($cy - $size))),
            (New-Object System.Drawing.PointF(($cx + $size * 0.3), $cy)),
            (New-Object System.Drawing.PointF(($cx - $size * 0.4), ($cy + $size)))
        ))
    }
    $pen.Dispose()
}

# ------------------------------------------------------------ home screens

# Shared chip fonts (used by every screen).
$chipFont = F 'Segoe UI' 15
$chipVal = F 'Segoe UI' 26 ([System.Drawing.FontStyle]::Bold)

function Render-Home([string]$name, [bool]$withScores, [string]$natalPalace, [string]$natalLabel) {
    $canvas = New-Canvas
    $bmp = $canvas[0]; $g = $canvas[1]
    $y = 70

    $titleFont = F 'Georgia' 62 ([System.Drawing.FontStyle]::Bold)
    $g.DrawString('Dao Fortune', $titleFont, (Brush $Gold), $Margin, $y)
    $titleFont.Dispose()
    $y += 76
    $tagFont = F 'Segoe UI' 19
    $g.DrawString('D A I L Y   L U O P A N   W I S D O M', $tagFont, (Brush $Muted), $Margin, $y)
    $tagFont.Dispose()
    $y += 58
    $dateFont = F 'Segoe UI' 34
    $g.DrawString($sample.date, $dateFont, (Brush $Parchment), $Margin, $y)
    $dateFont.Dispose()
    $y += 44
    $metaFont = F 'Segoe UI' 22
    $g.DrawString("Local time $($sample.time) $Dot Asia/Shanghai", $metaFont, (Brush $Muted), $Margin, $y)
    $metaFont.Dispose()
    $y += 52

    # the day selector
    $selH = 84
    RoundRect $g $Margin $y ($W - 2 * $Margin) $selH 18 $Card $Outline
    $selFont = F 'Segoe UI' 24
    $selSmall = F 'Segoe UI' 17
    $g.DrawString('Today', $selFont, (Brush $Parchment), ($Margin + 84), ($y + 14))
    $g.DrawString($sample.date, $selSmall, (Brush $Muted), ($Margin + 84), ($y + 48))
    Draw-Chevron $g ($Margin + 44) ($y + 42) 13 $Muted $true
    Draw-Chevron $g (($W - $Margin) - 44) ($y + 42) 13 $Muted $false
    $y += $selH + 16

    # the year sign
    RoundRect $g $Margin $y ($W - 2 * $Margin) $selH 18 $Card $Outline
    $g.DrawString('YEAR SIGN', $chipFont, (Brush $Muted), ($Margin + 24), ($y + 14))
    $g.DrawString($natalLabel, $selFont, (Brush $Gold), ($Margin + 24), ($y + 40))
    $g.DrawString('CHANGE', $chipFont, (Brush $Muted), (($W - $Margin) - 110), ($y + 40))
    $selFont.Dispose(); $selSmall.Dispose()
    $y += $selH + 16

    # chips
    $chipW = 470
    $chipH = 92
    RoundRect $g $Margin $y $chipW $chipH 18 $Card $Outline
    $g.DrawString('SOLAR TERM', $chipFont, (Brush $Muted), ($Margin + 22), ($y + 16))
    $g.DrawString($plate.term, $chipVal, (Brush $Gold), ($Margin + 22), ($y + 42))
    RoundRect $g ($Margin + $chipW + 24) $y $chipW $chipH 18 $Card $Outline
    $g.DrawString('DAY OFFICER', $chipFont, (Brush $Muted), ($Margin + $chipW + 46), ($y + 16))
    $g.DrawString($plate.officer, $chipVal, (Brush $Gold), ($Margin + $chipW + 46), ($y + 42))
    $y += $chipH + 22

    # pillars
    $pw = 232
    $ph = 92
    $px = $Margin
    $pillars = @(@('YEAR', $plate.year), @('MONTH', $plate.month), @('DAY', $plate.day), @('HOUR', $plate.hour))
    foreach ($p in $pillars) {
        RoundRect $g $px $y $pw $ph 18 $Card $Outline
        $g.DrawString($p[0], $chipFont, (Brush $Muted), ($px + 20), ($y + 14))
        $g.DrawString($p[1], $chipVal, (Brush $Gold), ($px + 20), ($y + 40))
        $px += $pw + 16
    }
    $y += $ph + 24

    # the plate card
    $cardH = 760
    RoundRect $g $Margin $y ($W - 2 * $Margin) $cardH 28 $Card $Outline
    $g.DrawString('THE PLATE', $chipFont, (Brush $Muted), ($Margin + 26), ($y + 24))
    $g.DrawString("Sun in $($plate.sunPalace) $Dot $($plate.sunElement)", $chipFont, (Brush $Gold),
                  (($W - $Margin) - 300), ($y + 24))
    $cx = $W / 2
    $cy = $y + 400
    Draw-Dial $g $cx $cy 275 $plate.sunPalace $plate.wealthElement $natalPalace
    $legendFont = F 'Segoe UI' 20
    $lx = $Margin + 34
    $g.FillEllipse((Brush $Gold), $lx, ($y + 690), 13, 13)
    $g.DrawString('Sun', $legendFont, (Brush $Muted), ($lx + 22), ($y + 683))
    $lx = $Margin + 160
    $g.FillEllipse((Brush $Jade), $lx, ($y + 690), 13, 13)
    $g.DrawString("Wealth $Dot $($plate.wealthElement)", $legendFont, (Brush $Muted), ($lx + 22), ($y + 683))
    $lx = $Margin + 430
    $g.FillEllipse((Brush $Crimson), $lx, ($y + 690), 13, 13)
    $g.DrawString('Avoid', $legendFont, (Brush $Muted), ($lx + 22), ($y + 683))
    if ($natalPalace) {
        $lx = $Margin + 590
        $ringPen = New-Object System.Drawing.Pen($Parchment, 3)
        $g.DrawEllipse($ringPen, $lx, ($y + 687), 19, 19)
        $ringPen.Dispose()
        $g.DrawString('Your sign', $legendFont, (Brush $Muted), ($lx + 28), ($y + 683))
    }
    $legendFont.Dispose()
    $y += $cardH + 34

    # the free-reading note (first-run state only)
    if (-not $withScores) {
        $bannerFont = F 'Segoe UI' 22
        $banner = 'Your first reading is free - open any module below. After that, each reading needs the one-off unlock. The verdicts are free; the reasoning behind them is not.'
        $usedHeight = Wrap $g $banner $bannerFont (Brush $Muted) $Margin $y ($W - 2 * $Margin)
        $y += $usedHeight + 16
        $bannerFont.Dispose()
    }

    # module cards
    $g.DrawString("TODAY'S READINGS", $chipFont, (Brush $Muted), $Margin, $y)
    $y += 34
    $modFont = F 'Segoe UI' 28 ([System.Drawing.FontStyle]::Bold)
    $scoreFont = F 'Georgia' 40 ([System.Drawing.FontStyle]::Bold)
    $previewFont = F 'Segoe UI' 14
    $headlineFont = F 'Segoe UI' 21
    foreach ($m in $plate.modules) {
        $accent = Verdict-Color $m.verdict
        RoundRect $g $Margin $y ($W - 2 * $Margin) 104 20 $Card $Outline
        $g.DrawString($m.title, $modFont, (Brush $Parchment), ($Margin + 26), ($y + 18))
        if ($withScores) {
            $g.DrawString($m.headline, $headlineFont, (Brush $Muted), ($Margin + 26), ($y + 58))
            $g.DrawString([string]$m.score, $scoreFont, (Brush $accent), (($W - $Margin) - 120), ($y + 26))
        } else {
            $g.DrawString('PREVIEW', $previewFont, (Brush $Muted), ($Margin + 26), ($y + 62))
            $g.DrawString($m.headline, $headlineFont, (Brush $Muted), ($Margin + 150), ($y + 56))
            # small padlock
            $lockX = $W - $Margin - 52
            $lockY = $y + 46
            $lockPen = New-Object System.Drawing.Pen($Muted, 3)
            $g.DrawArc($lockPen, ($lockX + 6), ($lockY - 10), 16, 16, 180, 180)
            $g.DrawLine($lockPen, ($lockX + 2), ($lockY + 4), ($lockX + 26), ($lockY + 4))
            $g.DrawLine($lockPen, ($lockX + 2), ($lockY + 4), ($lockX + 2), ($lockY + 20))
            $g.DrawLine($lockPen, ($lockX + 26), ($lockY + 4), ($lockX + 26), ($lockY + 20))
            $g.DrawLine($lockPen, ($lockX + 2), ($lockY + 20), ($lockX + 26), ($lockY + 20))
            $lockPen.Dispose()
        }
        $y += 116
    }
    $modFont.Dispose(); $scoreFont.Dispose(); $previewFont.Dispose(); $headlineFont.Dispose()
    Save-Png $bmp $g $name
}

Render-Home '01_home.png' $false '' 'Set yours to personalise the plate'
Render-Home '02_home_unlocked.png' $true 'Zi' "Rat $Dot Zi"

# ------------------------------------------------- screenshot 3: favourable days
#
# The home screen's own header, the day selector and the year-sign row, then
# the forward scan and the disclaimer card - the part of the app a reader
# scrolls down to.

$canvas = New-Canvas
$bmp = $canvas[0]; $g = $canvas[1]
$y = 70

$titleFont = F 'Georgia' 62 ([System.Drawing.FontStyle]::Bold)
$g.DrawString('Dao Fortune', $titleFont, (Brush $Gold), $Margin, $y)
$titleFont.Dispose()
$y += 76
$tagFont = F 'Segoe UI' 19
$g.DrawString('D A I L Y   L U O P A N   W I S D O M', $tagFont, (Brush $Muted), $Margin, $y)
$tagFont.Dispose()
$y += 58
$dateFont = F 'Segoe UI' 34
$g.DrawString($sample.date, $dateFont, (Brush $Parchment), $Margin, $y)
$dateFont.Dispose()
$y += 44
$metaFont = F 'Segoe UI' 22
$g.DrawString("Local time $($sample.time) $Dot Asia/Shanghai", $metaFont, (Brush $Muted), $Margin, $y)
$metaFont.Dispose()
$y += 52

# the day selector
$selH = 84
RoundRect $g $Margin $y ($W - 2 * $Margin) $selH 18 $Card $Outline
$selFont = F 'Segoe UI' 24
$selSmall = F 'Segoe UI' 17
$g.DrawString('Today', $selFont, (Brush $Parchment), ($Margin + 84), ($y + 14))
$g.DrawString($sample.date, $selSmall, (Brush $Muted), ($Margin + 84), ($y + 48))
Draw-Chevron $g ($Margin + 44) ($y + 42) 13 $Muted $true
Draw-Chevron $g (($W - $Margin) - 44) ($y + 42) 13 $Muted $false
$y += $selH + 16

# the year sign
RoundRect $g $Margin $y ($W - 2 * $Margin) $selH 18 $Card $Outline
$g.DrawString('YEAR SIGN', $chipFont, (Brush $Muted), ($Margin + 24), ($y + 14))
$g.DrawString("Rat $Dot Zi", $selFont, (Brush $Gold), ($Margin + 24), ($y + 40))
$g.DrawString('CHANGE', $chipFont, (Brush $Muted), (($W - $Margin) - 110), ($y + 40))
$selFont.Dispose(); $selSmall.Dispose()
$y += $selH + 30

$g.DrawString('FAVOURABLE DAYS AHEAD', $chipFont, (Brush $Gold), $Margin, $y)
$y += 40
$leadFont = F 'Segoe UI' 26
$usedHeight = Wrap $g 'The next sixty days, ranked by the windfall reading. Tap a day to read its plate.' $leadFont (Brush $Muted) $Margin $y ($W - 2 * $Margin)
$y += $usedHeight + 26

$dayFont = F 'Segoe UI' 34 ([System.Drawing.FontStyle]::Bold)
$dayMetaFont = F 'Segoe UI' 21
$dayScoreFont = F 'Georgia' 52 ([System.Drawing.FontStyle]::Bold)
foreach ($d in $sample.favourableDays) {
    $accent = Verdict-Color $d.verdict
    RoundRect $g $Margin $y ($W - 2 * $Margin) 158 18 $Card $Outline
    $g.DrawString($d.date, $dayFont, (Brush $Parchment), ($Margin + 28), ($y + 24))
    $g.DrawString("$($d.dayPillar) $Dot $($d.term)", $dayMetaFont, (Brush $Muted), ($Margin + 28), ($y + 74))
    $g.DrawString($d.headline, $dayMetaFont, (Brush $Muted), ($Margin + 28), ($y + 106))
    $g.DrawString([string]$d.score, $dayScoreFont, (Brush $accent), (($W - $Margin) - 150), ($y + 44))
    $y += 170
}
$y += 14

# the disclaimer, where the app puts it
$discFont = F 'Segoe UI' 21
$discText = 'Dao Fortune is a cultural tool built on the classical Chinese luopan tradition. It is offered for entertainment and reflection. It is not financial, medical, legal or psychological advice, and it does not predict events. You are always responsible for your own decisions.'
RoundRect $g $Margin $y ($W - 2 * $Margin) 190 22 $Card $Outline
$g.DrawString('DISCLAIMER', $chipFont, (Brush $Muted), ($Margin + 28), ($y + 24))
Wrap $g $discText $discFont (Brush $Muted) ($Margin + 28) ($y + 58) ($W - 2 * $Margin - 56) | Out-Null

$dayFont.Dispose(); $dayMetaFont.Dispose(); $dayScoreFont.Dispose(); $leadFont.Dispose()
$discFont.Dispose()
Save-Png $bmp $g '03_favourable_days.png'

# -------------------------------------------------------- screenshot 4: reading

$module = $plate.modules | Where-Object { $_.title -like '*Windfall*' } | Select-Object -First 1
$canvas = New-Canvas
$bmp = $canvas[0]; $g = $canvas[1]
$y = 110

$g.DrawString('WINDFALL & WEALTH', $chipFont, (Brush $Muted), $Margin, $y)
$y += 40
Draw-Gauge $g ($Margin + 110) ($y + 120) 100 ([int]$module.score) (Verdict-Color $module.verdict)
$scoreBig = F 'Georgia' 74 ([System.Drawing.FontStyle]::Bold)
$g.DrawString([string]$module.score, $scoreBig, (Brush (Verdict-Color $module.verdict)), ($Margin + 62), ($y + 78))
$g.DrawString($module.verdict.ToUpper(), (F 'Segoe UI' 20), (Brush $Muted), ($Margin + 58), ($y + 166))
$headFont = F 'Georgia' 46 ([System.Drawing.FontStyle]::Bold)
$hh = Wrap $g $module.headline $headFont (Brush (Verdict-Color $module.verdict)) ($Margin + 250) ($y + 28) 760
$scoreBig.Dispose(); $headFont.Dispose()
$y += 280

$bodyFont = F 'Segoe UI' 27
$body = Brush $Parchment
$mutedB = Brush $Muted
$usedHeight = Wrap $g $module.summary $bodyFont $body $Margin $y ($W - 2 * $Margin)
$y += $usedHeight + 40

$g.DrawString('WHAT THE PLATE SAYS', $chipFont, (Brush $Gold), $Margin, $y)
$y += 36
$bulletFont = F 'Segoe UI' 25
foreach ($line in $module.insights) {
    $g.FillEllipse((Brush $Gold), ($Margin + 2), ($y + 12), 10, 10)
    $usedHeight = Wrap $g $line $bulletFont $body ($Margin + 30) $y ($W - 2 * $Margin - 30)
    $y += $usedHeight + 14
}
$y += 20

$boxH = 300
RoundRect $g $Margin $y ($W - 2 * $Margin) $boxH 24 $CardHigh $Outline
$g.DrawString('BEST WINDOW', $chipFont, (Brush $Muted), ($Margin + 26), ($y + 24))
$hourFont = F 'Segoe UI' 24
$hy = $y + 58
foreach ($hr in $module.hours) {
    $g.DrawString("$($hr.range)  $Dot  $($hr.branch) hour", $hourFont, $body, ($Margin + 26), $hy)
    $g.DrawString($hr.officer, $hourFont, (Brush $Jade), (($W - $Margin) - 300), $hy)
    $hy += 40
}
$hy += 16
$g.FillEllipse((Brush $Jade), ($Margin + 30), ($hy + 8), 12, 12)
$g.DrawString("Turn towards  $($plate.bestDirection)", $hourFont, (Brush $Jade), ($Margin + 54), $hy)
$hy += 40
$g.FillEllipse((Brush $Crimson), ($Margin + 30), ($hy + 8), 12, 12)
$g.DrawString("Turn away from  $($plate.avoidDirection)", $hourFont, (Brush $Crimson), ($Margin + 54), $hy)
$y += $boxH + 36

$g.DrawString('WHAT TO DO', $chipFont, (Brush $Gold), $Margin, $y)
$y += 36
foreach ($line in $module.advice) {
    $g.FillEllipse((Brush $Gold), ($Margin + 2), ($y + 12), 10, 10)
    $usedHeight = Wrap $g $line $bulletFont $body ($Margin + 30) $y ($W - 2 * $Margin - 30)
    $y += $usedHeight + 14
}
$bodyFont.Dispose(); $bulletFont.Dispose(); $hourFont.Dispose()
Save-Png $bmp $g '04_windfall_reading.png'

# --------------------------------------------------------- screenshot 5: paywall

$canvas = New-Canvas
$bmp = $canvas[0]; $g = $canvas[1]
$y = 240

$bigFont = F 'Georgia' 62 ([System.Drawing.FontStyle]::Bold)
$hh = Wrap $g 'Unlock Full Access' $bigFont (Brush $Gold) $Margin $y ($W - 2 * $Margin)
$y += $hh + 24
$usedHeight = Wrap $g 'One payment. No subscription. Every module, every day, forever.' (F 'Segoe UI' 27) $mutedB $Margin $y ($W - 2 * $Margin)
$y += $usedHeight + 44

$benefits = @(
  'All five modules: overall fortune, windfall, love, career and body',
  'The complete luopan plate every day, with the sun''s mountain',
  'Any day you choose, plus the next sixty days scanned and ranked',
  'Every favourable double-hour, with the reason for each score',
  'The full factor breakdown behind every reading',
  'No subscription, no ads, no account'
)
$benefitFont = F 'Segoe UI' 25
$boxH = 40 + ($benefits.Count * 78)
RoundRect $g $Margin $y ($W - 2 * $Margin) $boxH 24 $CardHigh $Outline
$by = $y + 30
foreach ($b in $benefits) {
    $g.FillEllipse((Brush $Gold), ($Margin + 30), ($by + 10), 12, 12)
    $usedHeight = Wrap $g $b $benefitFont $body ($Margin + 56) $by ($W - 2 * $Margin - 90)
    $by += $usedHeight + 22
}
$y += $boxH + 60

$btnH = 110
RoundRect $g $Margin $y ($W - 2 * $Margin) $btnH 26 $Gold ([System.Drawing.Color]::FromArgb(0, 0, 0, 0))
$btnFont = F 'Segoe UI' 32 ([System.Drawing.FontStyle]::Bold)
$fmt = New-Object System.Drawing.StringFormat
$fmt.Alignment = [System.Drawing.StringAlignment]::Center
$fmt.LineAlignment = [System.Drawing.StringAlignment]::Center
$g.DrawString('Unlock for $9.99', $btnFont, (Brush ([System.Drawing.Color]::FromArgb(255, 26, 20, 8))),
              (New-Object System.Drawing.RectangleF($Margin, $y, ($W - 2 * $Margin), $btnH)), $fmt)
$y += $btnH + 22

RoundRect $g $Margin $y ($W - 2 * $Margin) 92 26 ([System.Drawing.Color]::FromArgb(0, 0, 0, 0)) $Gold
$smallFont = F 'Segoe UI' 26
$g.DrawString('Restore purchase', $smallFont, (Brush $Gold),
              (New-Object System.Drawing.RectangleF($Margin, $y, ($W - 2 * $Margin), 92)), $fmt)
$y += 92 + 30
$g.DrawString('Not now', $smallFont, $mutedB,
              (New-Object System.Drawing.RectangleF($Margin, $y, ($W - 2 * $Margin), 70)), $fmt)
$y += 100

$fine = F 'Segoe UI' 20
$usedHeight = Wrap $g 'Payments are handled by Google Play. Your year sign is stored on this device only and is never transmitted.' $fine $mutedB $Margin $y ($W - 2 * $Margin)
$y += $usedHeight + 30
$usedHeight = Wrap $g 'Dao Fortune is a cultural and entertainment product. It is not financial, medical, legal or psychological advice, and it does not predict events.' $fine $mutedB $Margin $y ($W - 2 * $Margin)

$bigFont.Dispose(); $btnFont.Dispose(); $smallFont.Dispose(); $fine.Dispose(); $benefitFont.Dispose()
Save-Png $bmp $g '05_unlock.png'

# ---------------------------------------------------------- screenshot 6: dream
#
# The dream sheet with its ink-wash plate. The ink is seeded by the dream's own
# text with the same forty-eight bit generator the app uses in
# ui/DreamSheet.kt, so this is the picture the reader would really see.

$script:InkMask = [System.Numerics.BigInteger]::Pow(2, 48) - 1
$script:InkMul  = [System.Numerics.BigInteger]25214903917   # 0x5DEECE66D
$script:InkAdd  = [System.Numerics.BigInteger]11             # 0xB

function New-InkRandom([int]$seed) {
    # The app sign-extends the thirty-two bit seed before the xor.
    $signed = [long]$seed
    $xored = $signed -bxor 25214903917L
    $state = [System.Numerics.BigInteger]$xored
    $state = $state -band $script:InkMask
    return [pscustomobject]@{ State = $state }
}

function Get-InkNext($rng) {
    # Every step is its own statement: in PowerShell the bitwise operators bind
    # tighter than the arithmetic ones, so a one-liner would mask the wrong
    # value.
    $next = $rng.State * $script:InkMul
    $next = $next + $script:InkAdd
    $next = $next -band $script:InkMask
    $rng.State = $next
    $value = $rng.State -shr 16
    $value = $value -band 2147483647
    return [double]$value / 2147483647.0
}

function Has-Cjk([string]$text) {
    foreach ($ch in $text.ToCharArray()) {
        if ([int][char]$ch -gt 0x2E80) { return $true }
    }
    return $false
}

function Draw-DreamPlate($g, [single]$cx, [single]$cy, [single]$r, $dream, [string]$sunPalace, [string]$wealthElement, [string]$natalPalace) {
    $rng = New-InkRandom ([int]$dream.seed)

    # --- the ink wash: seven soft fields, in the order the app draws them
    for ($i = 0; $i -lt 7; $i++) {
        $angle = (Get-InkNext $rng) * 6.2831853
        $distance = $r * (0.18 + (Get-InkNext $rng) * 0.62)
        $washAlpha = 0.018 + (Get-InkNext $rng) * 0.03
        $washRadius = $r * (0.22 + (Get-InkNext $rng) * 0.38)
        $bx = $cx + $distance * [Math]::Cos($angle)
        $by = $cy + $distance * [Math]::Sin($angle)
        $wash = [System.Drawing.Color]::FromArgb([int](255 * $washAlpha), 236, 231, 220)
        # A wash that fades out at its edge, the way the app draws it.
        $washPath = New-Object System.Drawing.Drawing2D.GraphicsPath
        $washPath.AddEllipse(($bx - $washRadius), ($by - $washRadius), ($washRadius * 2), ($washRadius * 2))
        $washBrush = New-Object System.Drawing.Drawing2D.PathGradientBrush($washPath)
        $washBrush.CenterPoint = New-Object System.Drawing.PointF($bx, $by)
        $washBrush.CenterColor = $wash
        $washBrush.SurroundColors = @([System.Drawing.Color]::FromArgb(0, 236, 231, 220))
        $g.FillEllipse($washBrush, ($bx - $washRadius), ($by - $washRadius), ($washRadius * 2), ($washRadius * 2))
        $washBrush.Dispose(); $washPath.Dispose()
    }

    # --- the wealth sector of the day
    $sector = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(46, 111, 191, 160), ($r * 0.033))
    foreach ($wb in (Wealth-Bearings $wealthElement)) {
        $g.DrawArc($sector, ($cx - $r * 0.86), ($cy - $r * 0.86), ($r * 1.72), ($r * 1.72), ($wb - 7.5 - 90), 15.0)
    }
    $sector.Dispose()

    # --- the rings
    $ring = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(51, 217, 179, 107), 3)
    $g.DrawEllipse($ring, ($cx - $r), ($cy - $r), ($r * 2), ($r * 2))
    $inner = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(31, 217, 179, 107), 2)
    $g.DrawEllipse($inner, ($cx - $r * 0.60), ($cy - $r * 0.60), ($r * 1.20), ($r * 1.20))
    $ring.Dispose(); $inner.Dispose()

    # --- the ticks and the mountain names
    $tick = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(77, 217, 179, 107), 2)
    $nameFont = F 'Segoe UI' 19
    $nameBrush = Brush ([System.Drawing.Color]::FromArgb(179, 154, 163, 175))
    $centerFmt = New-Object System.Drawing.StringFormat
    $centerFmt.Alignment = [System.Drawing.StringAlignment]::Center
    $centerFmt.LineAlignment = [System.Drawing.StringAlignment]::Center
    for ($i = 0; $i -lt 24; $i++) {
        $deg = $i * 15.0
        $a = ($deg - 90) * [Math]::PI / 180.0
        $x1 = $cx + ($r - 9) * [Math]::Cos($a)
        $y1 = $cy + ($r - 9) * [Math]::Sin($a)
        $x2 = $cx + ($r - 20) * [Math]::Cos($a)
        $y2 = $cy + ($r - 20) * [Math]::Sin($a)
        $g.DrawLine($tick, $x1, $y1, $x2, $y2)
        $lr = $r * 0.80
        $lx = $cx + $lr * [Math]::Cos($a)
        $ly = $cy + $lr * [Math]::Sin($a)
        $g.DrawString($Mountains[$i], $nameFont, $nameBrush, $lx, $ly, $centerFmt)
    }
    $tick.Dispose(); $nameFont.Dispose(); $nameBrush.Dispose(); $centerFmt.Dispose()

    # --- the images of the dream, laid on the mountains they belong to
    $cjkFont = F 'Microsoft YaHei UI' 34
    $termFont = F 'Segoe UI' 18
    $byPalace = @{}
    foreach ($tok in $dream.tokens) {
        if (-not $tok.palace) { continue }
        $key = [string]$tok.palace
        if (-not $byPalace.ContainsKey($key)) { $byPalace[$key] = @() }
        $byPalace[$key] += $tok
    }
    foreach ($palaceName in $byPalace.Keys) {
        $group = $byPalace[$palaceName]
        $bearing = [double]$group[0].bearing
        $a = ($bearing - 90) * [Math]::PI / 180.0
        for ($i = 0; $i -lt $group.Count; $i++) {
            $tok = $group[$i]
            $isPrincipal = [bool]$tok.principal
            $step = ($i - (($group.Count - 1) / 2.0)) * 40
            $px = $cx + ($r + 46) * [Math]::Cos($a) - $step * [Math]::Sin($a)
            $py = $cy + ($r + 46) * [Math]::Sin($a) + $step * [Math]::Cos($a)

            # the brush mark
            $markPen = New-Object System.Drawing.Pen($Parchment, 3)
            $g.DrawCurve($markPen, @(
                (New-Object System.Drawing.PointF(($px - 20), ($py + 14))),
                (New-Object System.Drawing.PointF(($px - 5), ($py - 14))),
                (New-Object System.Drawing.PointF(($px + 9), ($py + 8))),
                (New-Object System.Drawing.PointF(($px + 22), ($py - 13)))
            ))
            $markPen.Dispose()

            # the original term, and the word the reader wrote
            if (Has-Cjk $tok.chinese) {
                $cjkBrush = Brush $Parchment
                $g.DrawString($tok.chinese, $cjkFont, $cjkBrush, ($px - 18), ($py - 64))
                $cjkBrush.Dispose()
            }
            $termBrush = Brush ([System.Drawing.Color]::FromArgb(150, 154, 163, 175))
            $g.DrawString($tok.term, $termFont, $termBrush, ($px - 24), ($py + 18))
            $termBrush.Dispose()
        }
    }
    $cjkFont.Dispose(); $termFont.Dispose()

    # --- the sun of the day: a gold diamond
    $sunIndex = [Array]::IndexOf($Mountains, $sunPalace)
    if ($sunIndex -ge 0) {
        $a = ($sunIndex * 15.0 - 90) * [Math]::PI / 180.0
        $sx = $cx + ($r + 46) * [Math]::Cos($a)
        $sy = $cy + ($r + 46) * [Math]::Sin($a)
        $diamond = @(
            (New-Object System.Drawing.PointF($sx, ($sy - 11))),
            (New-Object System.Drawing.PointF(($sx + 8), $sy)),
            (New-Object System.Drawing.PointF($sx, ($sy + 11))),
            (New-Object System.Drawing.PointF(($sx - 8), $sy))
        )
        $g.FillPolygon((Brush $Gold), $diamond)
    }

    # --- the reader's own year branch: a hollow ring
    if ($natalPalace) {
        $natalIndex = [Array]::IndexOf($Mountains, $natalPalace)
        if ($natalIndex -ge 0) {
            $a = ($natalIndex * 15.0 - 90) * [Math]::PI / 180.0
            $nx = $cx + ($r + 46) * [Math]::Cos($a)
            $ny = $cy + ($r + 46) * [Math]::Sin($a)
            $natalPen = New-Object System.Drawing.Pen($Parchment, 3)
            $g.DrawEllipse($natalPen, ($nx - 11), ($ny - 11), 22, 22)
            $natalPen.Dispose()
        }
    }

    # --- the centre: the day's own pillar
    $pillarFont = F 'Georgia' 40 ([System.Drawing.FontStyle]::Bold)
    $g.DrawString($dream.day, $pillarFont, (Brush $Parchment), ($cx - 62), ($cy - 34))
    $subFont = F 'Segoe UI' 20
    $g.DrawString("Wealth $Dot $wealthElement", $subFont, (Brush $Muted), ($cx - 66), ($cy + 14))
    $pillarFont.Dispose(); $subFont.Dispose()
}

$dream = $sample.dream
$canvas = New-Canvas
$bmp = $canvas[0]; $g = $canvas[1]
$y = 84

# the header of the sheet
$g.DrawString('DREAM & OMENS', $chipFont, (Brush $Gold), $Margin, $y)
$mengZhan = [string][char]0x68A6 + [string][char]0x5360
$cjkTitle = F 'Microsoft YaHei UI' 26
$g.DrawString($mengZhan, $cjkTitle, (Brush $Gold), ($W - $Margin - 56), ($y - 4))
$cjkTitle.Dispose()
$y += 46

$dreamTitle = F 'Georgia' 46 ([System.Drawing.FontStyle]::Bold)
$g.DrawString('Your dream, read against the day', $dreamTitle, (Brush $Parchment), $Margin, $y)
$dreamTitle.Dispose()
$y += 72

# what the reader wrote
$dreamCardH = 120
RoundRect $g $Margin $y ($W - 2 * $Margin) $dreamCardH 22 $Card $Outline
$dreamFont = F 'Segoe UI' 31
$dreamBrush = Brush $Parchment
$usedHeight = Wrap $g $dream.text $dreamFont $dreamBrush ($Margin + 28) ($y + 24) ($W - 2 * $Margin - 56)
$dreamFont.Dispose()
$y += $dreamCardH + 16

# when the reader woke
$wokeFont = F 'Segoe UI' 23
$g.DrawString("You woke at $($dream.wokeAt)  $Dot  $($dream.contextLabel)", $wokeFont, (Brush $Muted), $Margin, $y)
$wokeFont.Dispose()
$y += 52

# the score and the headline
$dreamVerdict = $dream.verdict
Draw-Gauge $g ($Margin + 98) ($y + 88) 88 ([int]$dream.score) (Verdict-Color $dreamVerdict)
$dreamScoreBig = F 'Georgia' 64 ([System.Drawing.FontStyle]::Bold)
$g.DrawString([string]$dream.score, $dreamScoreBig, (Brush (Verdict-Color $dreamVerdict)), ($Margin + 56), ($y + 48))
$dreamScoreBig.Dispose()
$g.DrawString($dreamVerdict.ToUpper(), (F 'Segoe UI' 19), (Brush $Muted), ($Margin + 48), ($y + 130))
$dreamHead = F 'Georgia' 42 ([System.Drawing.FontStyle]::Bold)
Wrap $g $dream.headline $dreamHead (Brush (Verdict-Color $dreamVerdict)) ($Margin + 230) ($y + 22) 760 | Out-Null
$dreamHead.Dispose()
$y += 200

# what the dream said
$summaryFont = F 'Segoe UI' 25
$usedHeight = Wrap $g $dream.summary $summaryFont $body $Margin $y ($W - 2 * $Margin)
$summaryFont.Dispose()
$y += $usedHeight + 90

# the plate. The images sit outside the ring, so the plate needs room above it.
$plateSize = 720
$plateCx = $W / 2
$plateCy = $y + ($plateSize / 2)
Draw-DreamPlate $g $plateCx $plateCy ($plateSize / 2 - 30) $dream $dream.sunPalace $dream.wealthElement $dream.natalPalace
$y += $plateSize + 26

# what the plate says
$g.DrawString('WHAT THE PLATE SAYS', $chipFont, (Brush $Gold), $Margin, $y)
$y += 36
$dreamBullet = F 'Segoe UI' 24
$shown = 0
foreach ($line in $dream.insights) {
    if ($shown -ge 2) { break }
    $g.FillEllipse((Brush $Gold), ($Margin + 2), ($y + 11), 10, 10)
    $usedHeight = Wrap $g $line $dreamBullet $body ($Margin + 30) $y ($W - 2 * $Margin - 30)
    $y += $usedHeight + 14
    $shown++
}
$dreamBullet.Dispose()

# the privacy line the app shows at the foot of the sheet
$privacyFont = F 'Segoe UI' 20
$privacy = 'Your words are read on this phone and never leave it. Nothing is stored, nothing is uploaded, and closing this sheet erases the dream.'
$usedHeight = Wrap $g $privacy $privacyFont $mutedB $Margin ($y + 16) ($W - 2 * $Margin)
$privacyFont.Dispose()

Save-Png $bmp $g '06_dream.png'

Write-Output "done"
