$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$root='C:\BlackBerryDev\Workspace\Cave'
$source=[Drawing.Bitmap]::FromFile((Join-Path $root 'experiments\mage-walk-trial\generated-atlas.png'))
$original=[Drawing.Bitmap]::FromFile((Join-Path $root 'res\mage.png'))
$output=New-Object Drawing.Bitmap(420,280,[Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g=[Drawing.Graphics]::FromImage($output)
try{
    if($source.Width%6 -ne 0){throw 'Atlas width cannot be split into six columns'}
    $bands=@();$start=-1
    for($y=0;$y -lt $source.Height;$y++){
        $count=0
        for($x=0;$x -lt $source.Width;$x++){if($source.GetPixel($x,$y).A -ge 128){$count++}}
        if($count -gt 15){if($start -lt 0){$start=$y}}
        elseif($start -ge 0){$bands+=,@($start,($y-1));$start=-1}
    }
    if($start -ge 0){$bands+=,@($start,($source.Height-1))}
    if($bands.Count -ne 4){throw ('Expected four separate sprite rows, got '+$bands.Count)}
    $minY=$original.Height;$maxY=-1
    for($y=0;$y -lt $original.Height;$y++){for($x=0;$x -lt $original.Width;$x++){
        if($original.GetPixel($x,$y).A -ge 16){$minY=[Math]::Min($minY,$y);$maxY=[Math]::Max($maxY,$y)}
    }}
    $maxHeight=0
    foreach($band in $bands){$maxHeight=[Math]::Max($maxHeight,($band[1]-$band[0]+1))}
    $scale=($maxY-$minY+1)/[double]$maxHeight
    $cellW=[int]($source.Width/6)
    $g.CompositingMode=[Drawing.Drawing2D.CompositingMode]::SourceCopy
    $g.InterpolationMode=[Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $g.PixelOffsetMode=[Drawing.Drawing2D.PixelOffsetMode]::Half
    for($row=0;$row -lt 4;$row++){
        $top=[Math]::Max(0,($bands[$row][0]-3));$bottom=[Math]::Min(($source.Height-1),($bands[$row][1]+3));$h=$bottom-$top+1
        $w=[int][Math]::Round($cellW*$scale);$dh=[int][Math]::Round($h*$scale)
        if($w -gt 66 -or $dh -gt 66){throw 'Sprites require smaller packing scale'}
        for($col=0;$col -lt 6;$col++){
            $dest=New-Object Drawing.Rectangle(([int]($col*70+(70-$w)/2)),([int]($row*70+$maxY+1+3*$scale-$dh)),$w,$dh)
            $g.DrawImage($source,$dest,($col*$cellW),$top,$cellW,$h,[Drawing.GraphicsUnit]::Pixel)
        }
        Write-Output ('Source row '+$row+': '+$top+'..'+$bottom)
    }
    $output.Save((Join-Path $root 'res\mage_walk_trial.png'),[Drawing.Imaging.ImageFormat]::Png)
    Write-Output ('Original visible height: '+($maxY-$minY+1)+'px; foot baseline: '+$maxY)
}finally{$g.Dispose();$output.Dispose();$original.Dispose();$source.Dispose()}
$bmp=[Drawing.Bitmap]::FromFile((Join-Path $root 'res\mage_walk_trial.png'))
try{
    for($row=0;$row -lt 4;$row++){for($col=0;$col -lt 6;$col++){
        $visible=0;$edge=0
        for($y=0;$y -lt 70;$y++){for($x=0;$x -lt 70;$x++){
            if($bmp.GetPixel($col*70+$x,$row*70+$y).A -gt 0){$visible++;if($x -eq 0 -or $x -eq 69 -or $y -eq 0 -or $y -eq 69){$edge++}}
        }}
        if($visible -eq 0 -or $edge -gt 0){throw "Invalid frame $col,$row"}
    }}
    '24 populated frames with transparent borders verified'
}finally{$bmp.Dispose()}