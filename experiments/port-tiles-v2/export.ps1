Add-Type -AssemblyName System.Drawing
$assetDir = $PSScriptRoot
$variants = ([IO.File]::ReadAllText((Join-Path $assetDir 'generation.json')) | ConvertFrom-Json).variants
$sheet = New-Object Drawing.Bitmap 900,640
$canvas = [Drawing.Graphics]::FromImage($sheet)
$canvas.Clear([Drawing.Color]::FromArgb(24,28,34))
$labelFont = New-Object Drawing.Font 'Arial',14
try {
    for ($i=0; $i -lt $variants.Count; $i++) {
        $variant = $variants[$i]
        $localSource = Join-Path $assetDir ('port_'+$variant.direction+'_source.png')
        if (!(Test-Path -LiteralPath $localSource)) { Copy-Item -LiteralPath $variant.path -Destination $localSource }
        $source = [Drawing.Image]::FromFile($localSource)
        $tile = New-Object Drawing.Bitmap 140,140
        $graphics = [Drawing.Graphics]::FromImage($tile)
        try {
            $graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            $graphics.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::HighQuality
            $graphics.DrawImage($source,0,0,140,140)
            $tile.Save((Join-Path $assetDir ('tile_port_'+$variant.direction+'.png')),[Drawing.Imaging.ImageFormat]::Png)
            $left = ($i % 3)*300
            $top = [Math]::Floor($i/3)*320
            $canvas.DrawString($variant.direction.ToUpper(),$labelFont,[Drawing.Brushes]::White,$left+10,$top+4)
            $path = New-Object Drawing.Drawing2D.GraphicsPath
            try {
                $path.AddPolygon([Drawing.PointF[]]@(
                    [Drawing.PointF]::new($left+150,$top+30),
                    [Drawing.PointF]::new($left+271.24,$top+100),
                    [Drawing.PointF]::new($left+271.24,$top+240),
                    [Drawing.PointF]::new($left+150,$top+310),
                    [Drawing.PointF]::new($left+28.76,$top+240),
                    [Drawing.PointF]::new($left+28.76,$top+100)))
                $state = $canvas.Save()
                $canvas.SetClip($path)
                $canvas.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
                $canvas.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::Half
                $canvas.DrawImage($tile,$left+10,$top+30,280,280)
                $canvas.Restore($state)
            } finally { $path.Dispose() }
        } finally { $graphics.Dispose(); $tile.Dispose(); $source.Dispose() }
    }
    $sheet.Save((Join-Path $assetDir 'preview_hex.png'),[Drawing.Imaging.ImageFormat]::Png)
} finally { $canvas.Dispose(); $sheet.Dispose(); $labelFont.Dispose() }
Write-Output 'Six 140x140 tiles exported.'

