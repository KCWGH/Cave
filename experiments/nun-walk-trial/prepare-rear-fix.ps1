$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$root='C:\BlackBerryDev\Workspace\Cave'
$dir=Join-Path $root 'experiments\nun-walk-trial'
$backup=Join-Path $dir 'nun-walk-before-rear-fix.png'
if(!(Test-Path $backup)){Copy-Item (Join-Path $root 'res\nun_walk_trial.png') $backup}
$source=[Drawing.Bitmap]::FromFile((Join-Path $dir 'rear-diagonal-generated.png'))
$before=[Drawing.Bitmap]::FromFile($backup)
$output=$before.Clone((New-Object Drawing.Rectangle(0,0,420,280)),[Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g=[Drawing.Graphics]::FromImage($output)
try {
    $g.CompositingMode=[Drawing.Drawing2D.CompositingMode]::SourceCopy
    $bands=@();$start=-1
    for($y=0;$y -lt $source.Height;$y++) {
        $count=0
        for($x=0;$x -lt $source.Width;$x++){if($source.GetPixel($x,$y).A -ge 128){$count++}}
        if($count -gt 15){if($start -lt 0){$start=$y}}
        elseif($start -ge 0){$bands+=,@($start,($y-1));$start=-1}
    }
    if($start -ge 0){$bands+=,@($start,($source.Height-1))}
    if($bands.Count -ne 4){throw 'Expected four rows'}
    $maxHeight=0
    foreach($band in $bands){$maxHeight=[Math]::Max($maxHeight,($band[1]-$band[0]+1))}
    $scale=46.0/$maxHeight
    $cellW=[int]($source.Width/2)
    $g.InterpolationMode=[Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $g.PixelOffsetMode=[Drawing.Drawing2D.PixelOffsetMode]::Half
    for($col=0;$col -lt 2;$col++){
        $left=($col+1)*$cellW;$right=-1
        for($y=0;$y -lt $source.Height;$y++){for($x=$col*$cellW;$x -lt ($col+1)*$cellW;$x++){
            if($source.GetPixel($x,$y).A -ge 16){$left=[Math]::Min($left,$x);$right=[Math]::Max($right,$x)}
        }}
        $left=[Math]::Max($col*$cellW,($left-3));$right=[Math]::Min((($col+1)*$cellW-1),($right+3))
        $sw=$right-$left+1;$dw=[int][Math]::Round($sw*$scale)
        if($dw -gt 66){throw 'Frame too wide'}
        for($row=0;$row -lt 4;$row++){
            $g.FillRectangle([Drawing.Brushes]::Transparent,(($col+1)*70),($row*70),70,70)
            $top=[Math]::Max(0,($bands[$row][0]-3));$bottom=[Math]::Min(($source.Height-1),($bands[$row][1]+3))
            $sh=$bottom-$top+1;$dh=[int][Math]::Round($sh*$scale)
            $dest=New-Object Drawing.Rectangle(([int](($col+1)*70+(70-$dw)/2)),([int]($row*70+58+3*$scale-$dh)),$dw,$dh)
            $g.DrawImage($source,$dest,$left,$top,$sw,$sh,[Drawing.GraphicsUnit]::Pixel)
        }
    }
    for($row=0;$row -lt 4;$row++){for($col=0;$col -lt 6;$col++){
        $visible=0
        for($y=0;$y -lt 70;$y++){for($x=0;$x -lt 70;$x++){
            $p=$output.GetPixel($col*70+$x,$row*70+$y)
            if($p.A -gt 0){$visible++;if($x -eq 0 -or $x -eq 69 -or $y -eq 0 -or $y -eq 69){throw 'Frame touches edge'}}
            if($col -ne 1 -and $col -ne 2 -and $p.ToArgb() -ne $before.GetPixel($col*70+$x,$row*70+$y).ToArgb()){throw 'Other direction changed'}
        }}
        if($visible -eq 0){throw 'Empty frame'}
    }}
    $output.Save((Join-Path $root 'res\nun_walk_trial.png'),[Drawing.Imaging.ImageFormat]::Png)
    'Verified 24 frames, transparent borders, and unchanged four other directions'
} finally {$g.Dispose();$output.Dispose();$before.Dispose();$source.Dispose()}
