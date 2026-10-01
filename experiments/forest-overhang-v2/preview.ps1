Add-Type -AssemblyName System.Drawing
$assetDir = $PSScriptRoot
$projectDir = Split-Path (Split-Path $assetDir -Parent) -Parent
$floor = [Drawing.Image]::FromFile((Join-Path $assetDir 'tile_forest_floor.png'))
$trees = [Drawing.Image]::FromFile((Join-Path $assetDir 'forest_trees_overlay.png'))
$grass = [Drawing.Image]::FromFile((Join-Path $projectDir 'res/tile_grass.png'))
$mountain = [Drawing.Image]::FromFile((Join-Path $projectDir 'res/tile_mountain.png'))
$unit = [Drawing.Image]::FromFile((Join-Path $projectDir 'res/warrior.png'))
$image = New-Object Drawing.Bitmap 900,870
$g = [Drawing.Graphics]::FromImage($image)
$pen = New-Object Drawing.Pen ([Drawing.Color]::FromArgb(40,40,40)),2
$font = New-Object Drawing.Font 'Arial',15
$tiles = @(
    @{Q=0;R=0;Forest=$true},
    @{Q=1;R=0;Forest=$false;Mountain=$true},
    @{Q=1;R=-1;Forest=$true},
    @{Q=0;R=-1;Forest=$false},
    @{Q=-1;R=0;Forest=$true},
    @{Q=-1;R=1;Forest=$false},
    @{Q=0;R=1;Forest=$true}
)
try {
    $g.Clear([Drawing.Color]::FromArgb(24,28,34))
    $g.DrawString('FOREST OVERHANG - 2x PREVIEW',$font,[Drawing.Brushes]::White,12,8)
    $g.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $g.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::Half
    foreach ($tile in $tiles) {
        $cx=450+242.487*$tile.Q+121.244*$tile.R
        $cy=470+210*$tile.R
        $path = New-Object Drawing.Drawing2D.GraphicsPath
        try {
            $path.AddPolygon([Drawing.PointF[]]@(
                [Drawing.PointF]::new($cx,$cy-140),
                [Drawing.PointF]::new($cx+121.244,$cy-70),
                [Drawing.PointF]::new($cx+121.244,$cy+70),
                [Drawing.PointF]::new($cx,$cy+140),
                [Drawing.PointF]::new($cx-121.244,$cy+70),
                [Drawing.PointF]::new($cx-121.244,$cy-70)))
            $state=$g.Save()
            $g.SetClip($path)
            if ($tile.Forest) {$g.DrawImage($floor,[single]($cx-140),[single]($cy-140),[single]280,[single]280)}
            elseif ($tile.Mountain) {$g.DrawImage($mountain,[single]($cx-140),[single]($cy-140),[single]280,[single]280)}
            else {$g.DrawImage($grass,[single]($cx-140),[single]($cy-140),[single]280,[single]280)}
            $g.Restore($state)
            $g.DrawPath($pen,$path)
        } finally {$path.Dispose()}
    }
    foreach ($tile in ($tiles | Sort-Object R,Q)) {
        if (!$tile.Forest) {continue}
        $cx=450+242.487*$tile.Q+121.244*$tile.R
        $cy=470+210*$tile.R
        $g.DrawImage($trees,[single]($cx-120),[single]($cy-230),[single]240,[single]240)
    }
    $g.DrawImage($unit,380,380,140,140)
    $image.Save((Join-Path $assetDir 'preview_map.png'),[Drawing.Imaging.ImageFormat]::Png)
} finally {$g.Dispose();$image.Dispose();$pen.Dispose();$font.Dispose();$floor.Dispose();$trees.Dispose();$grass.Dispose();$unit.Dispose();$mountain.Dispose()}

