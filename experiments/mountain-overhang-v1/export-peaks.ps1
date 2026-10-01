param([string]$Name)
Add-Type -AssemblyName System.Drawing
$source = [Drawing.Bitmap]::FromFile((Join-Path $PSScriptRoot ($Name+'_source.png')))
$output = New-Object Drawing.Bitmap 140,190
$g = [Drawing.Graphics]::FromImage($output)
try {
    $left=$source.Width; $top=$source.Height; $right=0; $bottom=0
    for ($y=0;$y -lt $source.Height;$y++) { for ($x=0;$x -lt $source.Width;$x++) {
        if ($source.GetPixel($x,$y).A -gt 0) {
            $left=[Math]::Min($left,$x); $top=[Math]::Min($top,$y)
            $right=[Math]::Max($right,$x); $bottom=[Math]::Max($bottom,$y)
        }
    }}
    if ($right -le $left -or $bottom -le $top) {throw 'Empty tree overlay'}
    $width=$right-$left+1; $height=$bottom-$top+1
    $scale=[Math]::Min(136.0/$width,186.0/$height)
    $drawWidth=[single]($width*$scale); $drawHeight=[single]186
    $g.Clear([Drawing.Color]::Transparent)
    $g.InterpolationMode=[Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode=[Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $dest=[Drawing.RectangleF]::new((140-$drawWidth)/2,2,$drawWidth,$drawHeight)
    $g.DrawImage($source,$dest,[Drawing.RectangleF]::new($left,$top,$width,$height),[Drawing.GraphicsUnit]::Pixel)
    $output.Save((Join-Path $PSScriptRoot ($Name+'.png')),[Drawing.Imaging.ImageFormat]::Png)
} finally {$g.Dispose();$output.Dispose();$source.Dispose()}
