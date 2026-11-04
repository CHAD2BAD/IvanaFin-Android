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

# 2) TV banner 640x360 (320x180dp @ xhdpi): the launcher shows apps as landscape tiles,
#    so the photo fills the whole tile (16:9 crop, kept centred on the face)
$W = 640; $H = 360
$bmp, $g = New-Canvas $W $H
$bw = $src.Width; $bh = [int]($src.Width * $H / $W)
if ($bh -gt $src.Height) { $bh = $src.Height; $bw = [int]($src.Height * $W / $H) }
$bx = [int][Math]::Min([Math]::Max($src.Width * $cropCenterX - $bw / 2, 0), $src.Width - $bw)
$by = [int](($src.Height - $bh) / 2)
$g.DrawImage($src, (New-Object System.Drawing.Rectangle 0, 0, $W, $H), (New-Object System.Drawing.Rectangle $bx, $by, $bw, $bh), 'Pixel')
$g.Dispose(); $bmp.Save((Join-Path $out 'ivanafin_banner.png'), [System.Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose()

# 3) Plain PNG launcher assets under fresh names (Fire TV's launcher draws the square icon
#    on a tile and caches by resource, so new names force it to pick up the new picture)
$mip = Join-Path $root 'app\src\default\res\mipmap-xhdpi'
New-Item -ItemType Directory -Force $mip | Out-Null
$bmp, $g = New-Canvas 192 192
$g.DrawImage($src, (New-Object System.Drawing.Rectangle 0, 0, 192, 192), $srcRect, 'Pixel')
$g.Dispose(); $bmp.Save((Join-Path $mip 'ivanafin_launcher.png'), [System.Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose()
Copy-Item (Join-Path $out 'ivanafin_banner.png') (Join-Path $mip 'ivanafin_tv_banner.png') -Force
Get-ChildItem $mip | ForEach-Object { "{0} {1:N0} bytes" -f $_.Name, $_.Length }

$src.Dispose()
Get-ChildItem $out | ForEach-Object { "{0} {1:N0} bytes" -f $_.Name, $_.Length }
