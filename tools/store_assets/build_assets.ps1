# Builds the Google Play store assets from code: icon, feature graphic and
# phone screenshots that mirror the real app.
#
# Usage (PowerShell 5.1, run with -ExecutionPolicy Bypass):
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\store_assets\build_assets.ps1
#
# Requires System.Drawing (built into Windows). No extra packages.

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$OutDir = Join-Path $Root 'docs\store'
$ShotDir = Join-Path $OutDir 'screenshots'
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
New-Item -ItemType Directory -Force -Path $ShotDir | Out-Null

$Ink        = [System.Drawing.Color]::FromArgb(255, 11, 14, 19)
$InkHigh    = [System.Drawing.Color]::FromArgb(255, 20, 25, 34)
$Gold       = [System.Drawing.Color]::FromArgb(255, 217, 179, 107)
$GoldDim    = [System.Drawing.Color]::FromArgb(120, 217, 179, 107)
$GoldFaint  = [System.Drawing.Color]::FromArgb(48, 217, 179, 107)
$Jade       = [System.Drawing.Color]::FromArgb(255, 111, 191, 160)
$Crimson    = [System.Drawing.Color]::FromArgb(255, 210, 112, 95)
$Parchment  = [System.Drawing.Color]::FromArgb(255, 236, 231, 220)
$Muted      = [System.Drawing.Color]::FromArgb(255, 154, 163, 175)
$Surface    = [System.Drawing.Color]::FromArgb(255, 20, 25, 34)

$Mountains = @('Zi','Gui','Chou','Gen','Yin','Jia','Mao','Yi','Chen','Xun','Si','Bing',
               'Wu','Ding','Wei','Kun','Shen','Geng','You','Xin','Xu','Qian','Hai','Ren')

function New-Canvas([int]$w, [int]$h) {
    $bmp = New-Object System.Drawing.Bitmap($w, $h)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    $g.Clear($Ink)
    return @($bmp, $g)
}

function Save-Png($bmp, $g, [string]$path) {
    $g.Dispose()
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Output ("wrote " + $path)
}

function New-FontPx([string]$family, [float]$size, [System.Drawing.FontStyle]$style = [System.Drawing.FontStyle]::Regular) {
    return New-Object System.Drawing.Font($family, $size, $style, [System.Drawing.GraphicsUnit]::Pixel)
}

# ---------------------------------------------------------------- the emblem

function Draw-Emblem($g, [single]$cx, [single]$cy, [single]$r, [bool]$withLabels = $true) {
    $outer = New-Object System.Drawing.Pen($Gold, ($r * 0.028))
    $inner = New-Object System.Drawing.Pen($GoldDim, ($r * 0.012))
    $hair  = New-Object System.Drawing.Pen($GoldFaint, ($r * 0.008))
    $g.DrawEllipse($outer, ($cx - $r), ($cy - $r), ($r * 2), ($r * 2))
    $g.DrawEllipse($hair,  ($cx - $r * 0.80), ($cy - $r * 0.80), ($r * 1.60), ($r * 1.60))
    $g.DrawEllipse($hair,  ($cx - $r * 0.30), ($cy - $r * 0.30), ($r * 0.60), ($r * 0.60))

    # 24 mountain ticks
    for ($i = 0; $i -lt 24; $i++) {
        $deg = $i * 15.0
        $a = ($deg - 90) * [Math]::PI / 180.0
        $major = ($i % 6 -eq 0)
        $len = if ($major) { $r * 0.13 } else { $r * 0.07 }
        $x1 = $cx + ($r - $r * 0.02) * [Math]::Cos($a)
        $y1 = $cy + ($r - $r * 0.02) * [Math]::Sin($a)
        $x2 = $cx + ($r - $r * 0.02 - $len) * [Math]::Cos($a)
        $y2 = $cy + ($r - $r * 0.02 - $len) * [Math]::Sin($a)
        $pen = if ($major) { $outer } else { $inner }
        $g.DrawLine($pen, $x1, $y1, $x2, $y2)
    }

    # the wealth arc (jade) in the east
    $sector = New-Object System.Drawing.Pen($Jade, ($r * 0.035))
    $g.DrawArc($sector, ($cx - $r * 0.86), ($cy - $r * 0.86), ($r * 1.72), ($r * 1.72), -135.0, 60.0)

    # the needle
    $goldBrush = New-Object System.Drawing.SolidBrush($Gold)
    $jadeBrush = New-Object System.Drawing.SolidBrush($Jade)
    $tipY = $cy - $r * 0.64
    $tailY = $cy + $r * 0.60
    $halfW = $r * 0.065
    $midY = $cy
    $lowY = $cy + $r * 0.16
    $pTip = New-Object System.Drawing.PointF($cx, $tipY)
    $pRight = New-Object System.Drawing.PointF(($cx + $halfW), $midY)
    $pTail = New-Object System.Drawing.PointF($cx, $tailY)
    $pLeft = New-Object System.Drawing.PointF(($cx - $halfW), $midY)
    $pLow = New-Object System.Drawing.PointF($cx, $lowY)
    $pts = @($pTip, $pRight, $pTail, $pLeft)
    $g.FillPolygon($goldBrush, $pts)
    $jade = @($pTail, $pRight, $pLow, $pLeft)
    $g.FillPolygon($jadeBrush, $jade)
    $g.DrawEllipse($inner, ($cx - $r * 0.055), ($cy - $r * 0.055), ($r * 0.11), ($r * 0.11))

    if ($withLabels) {
        $font = New-FontPx 'Segoe UI' ($r * 0.066)
        $brush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(190, 154, 163, 175))
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
    }

    $outer.Dispose(); $inner.Dispose(); $hair.Dispose(); $sector.Dispose()
    $goldBrush.Dispose(); $jadeBrush.Dispose()
}

# ------------------------------------------------------------------- icon

$canvas = New-Canvas 512 512
$bmp = $canvas[0]; $g = $canvas[1]
$grad = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
    (New-Object System.Drawing.Point(0, 0)), (New-Object System.Drawing.Point(512, 512)),
    $InkHigh, $Ink)
$g.FillRectangle($grad, 0, 0, 512, 512)
$grad.Dispose()
Draw-Emblem $g 256 256 218 $true
Save-Png $bmp $g (Join-Path $OutDir 'icon_512.png')

# --------------------------------------------------------- feature graphic

$canvas = New-Canvas 1024 500
$bmp = $canvas[0]; $g = $canvas[1]
$grad = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
    (New-Object System.Drawing.Point(0, 0)), (New-Object System.Drawing.Point(1024, 500)),
    $InkHigh, $Ink)
$g.FillRectangle($grad, 0, 0, 1024, 500)
$grad.Dispose()

# faint gold arc sweeping across the banner
$arcPen = New-Object System.Drawing.Pen($GoldFaint, 2)
$g.DrawArc($arcPen, 620, -420, 1100, 1100, 200.0, 140.0)
$g.DrawArc($arcPen, 700, -520, 1100, 1100, 205.0, 130.0)
$arcPen.Dispose()

Draw-Emblem $g 232 250 176 $false

$titleFont = New-FontPx 'Georgia' 72 ([System.Drawing.FontStyle]::Bold)
$subFont   = New-FontPx 'Segoe UI' 27
$bodyFont  = New-FontPx 'Segoe UI' 20
$goldBrush = New-Object System.Drawing.SolidBrush($Gold)
$parch     = New-Object System.Drawing.SolidBrush($Parchment)
$muted     = New-Object System.Drawing.SolidBrush($Muted)
$rulePen   = New-Object System.Drawing.Pen($Gold, 2)

$g.DrawString('Dao Fortune', $titleFont, $goldBrush, 448, 168)
$g.DrawLine($rulePen, 450, 262, 930, 262)
$g.DrawString('Daily Luopan Wisdom', $subFont, $parch, 450, 280)
$g.DrawString('Chinese luopan divination, computed for your exact moment.', $bodyFont, $muted, 450, 322)
$dot = [char]0x00B7
$g.DrawString("Windfall $dot Love $dot Career $dot Body $dot Overall fortune", $bodyFont, $muted, 450, 356)

$titleFont.Dispose(); $subFont.Dispose(); $bodyFont.Dispose()
$goldBrush.Dispose(); $parch.Dispose(); $muted.Dispose(); $rulePen.Dispose()
Save-Png $bmp $g (Join-Path $OutDir 'feature_graphic_1024x500.png')

Write-Output "done"
