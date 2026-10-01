Add-Type -AssemblyName System.Drawing
$source=[Drawing.Image]::FromFile((Join-Path $PSScriptRoot 'tile_mountain_floor_source.png'))
$output=New-Object Drawing.Bitmap 140,140
$g=[Drawing.Graphics]::FromImage($output)
try {
    $g.InterpolationMode=[Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode=[Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.DrawImage($source,0,0,140,140)
    $output.Save((Join-Path $PSScriptRoot 'tile_mountain_floor.png'),[Drawing.Imaging.ImageFormat]::Png)
} finally {$g.Dispose();$output.Dispose();$source.Dispose()}
