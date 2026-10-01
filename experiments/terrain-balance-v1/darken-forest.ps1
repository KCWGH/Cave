Add-Type -AssemblyName System.Drawing
$root=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
foreach($name in @('forest_dense','forest_clearing')) {
    $source=[Drawing.Image]::FromFile((Join-Path $root ('res/'+$name+'.png')))
    $output=New-Object Drawing.Bitmap $source.Width,$source.Height
    $g=[Drawing.Graphics]::FromImage($output)
    $attributes=New-Object Drawing.Imaging.ImageAttributes
    try {
        $matrix=New-Object Drawing.Imaging.ColorMatrix
        $matrix.Matrix00=0.9; $matrix.Matrix11=0.9; $matrix.Matrix22=0.9
        $attributes.SetColorMatrix($matrix)
        $g.CompositingMode=[Drawing.Drawing2D.CompositingMode]::SourceCopy
        $g.DrawImage($source,[Drawing.Rectangle]::new(0,0,$source.Width,$source.Height),0,0,$source.Width,$source.Height,[Drawing.GraphicsUnit]::Pixel,$attributes)
        $output.Save((Join-Path $PSScriptRoot ($name+'_dark.png')),[Drawing.Imaging.ImageFormat]::Png)
    } finally {$attributes.Dispose();$g.Dispose();$output.Dispose();$source.Dispose()}
}
