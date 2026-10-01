Add-Type -AssemblyName System.Drawing
$assetDir = $PSScriptRoot
$manifest = [IO.File]::ReadAllText((Join-Path $assetDir 'generation.json')) | ConvertFrom-Json
function Export-Layer($sourcePath,$name,$width,$height) {
    $localSource = Join-Path $assetDir ($name+'_source.png')
    if (!(Test-Path -LiteralPath $localSource)) { Copy-Item -LiteralPath $sourcePath -Destination $localSource }
    $source = [Drawing.Image]::FromFile($localSource)
    $output = New-Object Drawing.Bitmap $width,$height
    $graphics = [Drawing.Graphics]::FromImage($output)
    try {
        $graphics.Clear([Drawing.Color]::Transparent)
        $graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $graphics.DrawImage($source,0,0,$width,$height)
        $output.Save((Join-Path $assetDir ($name+'.png')),[Drawing.Imaging.ImageFormat]::Png)
    } finally { $graphics.Dispose(); $output.Dispose(); $source.Dispose() }
}
Export-Layer $manifest.floor.path 'tile_forest_floor' 140 140
Export-Layer $manifest.trees.path 'forest_trees_overlay' 140 180
$overlay = [Drawing.Bitmap]::FromFile((Join-Path $assetDir 'forest_trees_overlay.png'))
try {
    $transparentCount=0
    $visibleCount=0
    for($y=0;$y -lt $overlay.Height;$y++) { for($x=0;$x -lt $overlay.Width;$x++) {
        $alpha=$overlay.GetPixel($x,$y).A
        if($alpha -eq 0){$transparentCount++}
        if($alpha -gt 0){$visibleCount++}
    }}
    if($transparentCount -eq 0 -or $visibleCount -eq 0){throw 'Invalid overlay alpha'}
    Write-Output ('Overlay alpha: '+$transparentCount+' transparent; '+$visibleCount+' visible pixels')
} finally { $overlay.Dispose() }

