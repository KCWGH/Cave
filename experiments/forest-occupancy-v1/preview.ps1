Add-Type -AssemblyName System.Drawing
$root=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$output=New-Object Drawing.Bitmap 1000,650
$g=[Drawing.Graphics]::FromImage($output)
$pen=New-Object Drawing.Pen ([Drawing.Color]::FromArgb(40,40,40)),2
$floor=[Drawing.Image]::FromFile((Join-Path $root 'res/tile_forest_floor.png'))
try {
    $g.Clear([Drawing.Color]::FromArgb(24,28,34))
    $g.InterpolationMode=[Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $g.PixelOffsetMode=[Drawing.Drawing2D.PixelOffsetMode]::Half
    for($panel=0;$panel -lt 2;$panel++) {
        $cx=250+500*$panel; $cy=345
        $path=New-Object Drawing.Drawing2D.GraphicsPath
        $path.AddPolygon([Drawing.PointF[]]@([Drawing.PointF]::new($cx,$cy-210),[Drawing.PointF]::new($cx+182,$cy-105),[Drawing.PointF]::new($cx+182,$cy+105),[Drawing.PointF]::new($cx,$cy+210),[Drawing.PointF]::new($cx-182,$cy+105),[Drawing.PointF]::new($cx-182,$cy-105)))
        $state=$g.Save();$g.SetClip($path)
        $g.DrawImage($floor,$cx-210,$cy-210,420,420)
        $g.Restore($state);$g.DrawPath($pen,$path);$path.Dispose()
        $name=if($panel -eq 0){'forest_dense'}else{'forest_clearing'}
        $trees=[Drawing.Image]::FromFile((Join-Path $PSScriptRoot ($name+'.png')))
        $g.DrawImage($trees,$cx-210,$cy-345,420,570);$trees.Dispose()
        if($panel -eq 1) {
            $names=@('thief','mage','nun','warrior')
            for($i=0;$i -lt 4;$i++) {
                $unit=[Drawing.Image]::FromFile((Join-Path $root ('res/'+$names[$i]+'.png')))
                $dx=if($i%2 -eq 0){-15}else{15};$dy=if($i -lt 2){-10}else{15}
                $g.DrawImage($unit,$cx+3*($dx-35),$cy+3*($dy-45),210,210)
                $unit.Dispose()
            }
        }
    }
    $output.Save((Join-Path $PSScriptRoot 'preview.png'),[Drawing.Imaging.ImageFormat]::Png)
} finally {$floor.Dispose();$pen.Dispose();$g.Dispose();$output.Dispose()}
