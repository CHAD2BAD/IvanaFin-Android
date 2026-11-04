# IvanaFin: generates launcher icon + TV banner from branding\ivanafin-source.jpg
# Usage: powershell -File branding\make-icons.ps1
Add-Type -AssemblyName System.Drawing
$root = Split-Path -Parent $PSScriptRoot
$src  = [System.Drawing.Image]::FromFile((Join-Path $PSScriptRoot 'ivanafin-source-2.png'))
# Horizontal centre of the square crop, as a fraction of the photo width (cat's face sits right of centre)
$cropCenterX = 0.66
$out  = Join-Path $root 'app\src\default\res\drawable-nodpi'

function New-Canvas($w, $h) {
    $bmp = New-Object System.Drawing.Bitmap $w, $h
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = 'AntiAlias'; $g.InterpolationMode = 'HighQualityBicubic'
    $g.PixelOffsetMode = 'HighQuality'; $g.TextRenderingHint = 'AntiAliasGridFit'
    return $bmp, $g
}
function New-RoundRect($x, $y, $w, $h, $r) {
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $r * 2
    $p.AddArc($x, $y, $d, $d, 180, 90); $p.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $p.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90); $p.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
    $p.CloseFigure(); return $p
}

# Square crop of the source (it is already square; centre-crop just in case)
$side = [Math]::Min($src.Width, $src.Height)
$cropX = [int][Math]::Min([Math]::Max($src.Width * $cropCenterX - $side / 2, 0), $src.Width - $side)
$srcRect = New-Object System.Drawing.Rectangle $cropX, ([int](($src.Height - $side) / 2)), $side, $side

# 1) Adaptive-icon layer: full-bleed photo, 432x432 (108dp @ xxxhdpi). Launcher masks the shape.
$bmp, $g = New-Canvas 432 432
$g.DrawImage($src, (New-Object System.Drawing.Rectangle 0, 0, 432, 432), $srcRect, 'Pixel')
$g.Dispose(); $bmp.Save((Join-Path $out 'ivanafin_icon.png'), [System.Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose()

# 2) TV banner 640x360 (320x180dp @ xhdpi): black, rounded photo left, name right
$W = 640; $H = 360
$bmp, $g = New-Canvas $W $H
$g.Clear([System.Drawing.Color]::Black)
$pad = 40; $ph = $H - 2 * $pad
$clip = New-RoundRect $pad $pad $ph $ph 36
$g.SetClip($clip)
$g.DrawImage($src, (New-Object System.Drawing.Rectangle $pad, $pad, $ph, $ph), $srcRect, 'Pixel')
$g.ResetClip()
$pen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255, 0x52, 0x52, 0x52)), 3
$g.DrawPath($pen, $clip)
$font = New-Object System.Drawing.Font 'Segoe UI Semibold', 52, ([System.Drawing.FontStyle]::Regular), ([System.Drawing.GraphicsUnit]::Pixel)
$brush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 0xD1, 0xD5, 0xDB))
$textX = $pad + $ph + 28
$sf = New-Object System.Drawing.StringFormat; $sf.LineAlignment = 'Center'; $sf.Alignment = 'Near'
$g.DrawString('IvanaFin', $font, $brush, (New-Object System.Drawing.RectangleF $textX, 0, ($W - $textX - 16), $H), $sf)
$g.Dispose(); $bmp.Save((Join-Path $out 'ivanafin_banner.png'), [System.Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose()

$src.Dispose()
Get-ChildItem $out | ForEach-Object { "{0} {1:N0} bytes" -f $_.Name, $_.Length }
