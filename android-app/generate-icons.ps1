Add-Type -AssemblyName System.Drawing

$srcFile = "c:\Users\flooxie\Downloads\Compressed\code2\logo.png"
$resDir = "c:\Users\flooxie\Downloads\Compressed\code2\android-app\app\src\main\res"

$orig = [System.Drawing.Image]::FromFile($srcFile)
Write-Host "Source Logo Size: $($orig.Width)x$($orig.Height)"

$densities = @(
    @{ folder = "mipmap-mdpi"; size = 48; fgSize = 108 },
    @{ folder = "mipmap-hdpi"; size = 72; fgSize = 162 },
    @{ folder = "mipmap-xhdpi"; size = 96; fgSize = 216 },
    @{ folder = "mipmap-xxhdpi"; size = 144; fgSize = 324 },
    @{ folder = "mipmap-xxxhdpi"; size = 192; fgSize = 432 }
)

foreach ($d in $densities) {
    $dir = Join-Path $resDir $d.folder
    if (!(Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    
    $sz = $d.size
    $bmp = New-Object System.Drawing.Bitmap $sz, $sz
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.Clear([System.Drawing.Color]::Transparent)
    $g.DrawImage($orig, 0, 0, $sz, $sz)
    $g.Dispose()
    
    $outPath = Join-Path $dir "ic_launcher.png"
    $outRound = Join-Path $dir "ic_launcher_round.png"
    $bmp.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Save($outRound, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

# Generate 512x512 High-Res Adaptive Foreground (drawable/ic_launcher_foreground.png)
$drawableDir = Join-Path $resDir "drawable"
$fgBmp = New-Object System.Drawing.Bitmap 432, 432
$fgG = [System.Drawing.Graphics]::FromImage($fgBmp)
$fgG.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$fgG.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
$fgG.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$fgG.Clear([System.Drawing.Color]::Transparent)

# Adaptive foreground icon safe zone (centered ~216x216 in 432x432 canvas)
$targetLogoSize = 256
$offset = (432 - $targetLogoSize) / 2
$fgG.DrawImage($orig, [float]$offset, [float]$offset, [float]$targetLogoSize, [float]$targetLogoSize)
$fgG.Dispose()

$fgPath = Join-Path $drawableDir "ic_launcher_foreground.png"
$fgBmp.Save($fgPath, [System.Drawing.Imaging.ImageFormat]::Png)
$fgBmp.Dispose()

$orig.Dispose()
Write-Host "Generated crisp launcher icons and adaptive foreground successfully!"
