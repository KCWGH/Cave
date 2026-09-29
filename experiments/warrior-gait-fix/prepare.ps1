$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$root='C:\BlackBerryDev\Workspace\Cave'
$dir=Join-Path $root 'experiments\warrior-gait-fix'
function Pack($path,$cols,$rows){
    $src=[Drawing.Bitmap]::FromFile($path)
    $bmp=New-Object Drawing.Bitmap(($cols*70),($rows*70),[Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g=[Drawing.Graphics]::FromImage($bmp)
    try{
        $bands=@();$start=-1
        for($y=0;$y -lt $src.Height;$y++){
            $count=0
            for($x=0;$x -lt $src.Width;$x++){if($src.GetPixel($x,$y).A -ge 128){$count++}}
            if($count -gt 15){if($start -lt 0){$start=$y}}
            elseif($start -ge 0){$bands+=,@($start,($y-1));$start=-1}
        }
        if($start -ge 0){$bands+=,@($start,($src.Height-1))}
        if($bands.Count -ne $rows){throw ('Unexpected source row count: '+$bands.Count)}
        $maxH=0
        foreach($band in $bands){$maxH=[Math]::Max($maxH,($band[1]-$band[0]+1))}
        $scale=51.0/$maxH
        $g.CompositingMode=[Drawing.Drawing2D.CompositingMode]::SourceCopy
        $g.InterpolationMode=[Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
        $g.PixelOffsetMode=[Drawing.Drawing2D.PixelOffsetMode]::Half
        for($col=0;$col -lt $cols;$col++){
            $cx=[int][Math]::Round($col*$src.Width/[double]$cols)
            $end=[int][Math]::Round(($col+1)*$src.Width/[double]$cols)
            $left=$end;$right=-1
            for($y=0;$y -lt $src.Height;$y++){for($x=$cx;$x -lt $end;$x++){
                if($src.GetPixel($x,$y).A -ge 16){$left=[Math]::Min($left,$x);$right=[Math]::Max($right,$x)}
            }}
            $left=[Math]::Max($cx,($left-3));$right=[Math]::Min(($end-1),($right+3))
            $sw=$right-$left+1;$dw=[int][Math]::Round($sw*$scale)
            if($dw -gt 66){throw 'Packed frame too wide'}
            for($row=0;$row -lt $rows;$row++){
                $top=[Math]::Max(0,($bands[$row][0]-3));$bottom=[Math]::Min(($src.Height-1),($bands[$row][1]+3))
                $sh=$bottom-$top+1;$dh=[int][Math]::Round($sh*$scale)
                $dest=New-Object Drawing.Rectangle(([int]($col*70+(70-$dw)/2)),([int]($row*70+60+3*$scale-$dh)),$dw,$dh)
                $g.DrawImage($src,$dest,$left,$top,$sw,$sh,[Drawing.GraphicsUnit]::Pixel)
            }
        }
    }finally{$g.Dispose();$src.Dispose()}
    return ,$bmp
}
$before=[Drawing.Bitmap]::FromFile((Join-Path $dir 'warrior-before.png'))
$out=$before.Clone((New-Object Drawing.Rectangle(0,0,420,280)),[Drawing.Imaging.PixelFormat]::Format32bppArgb)
$w=Pack (Join-Path $dir 'generated-w.png') 1 4
$front=Pack (Join-Path $dir 'generated-front-opposite.png') 2 2
try{
    # Keep the accepted faces, equipment and torso. Integrate generated lower-leg regions.
    for($row=0;$row -lt 4;$row++){for($y=49;$y -lt 70;$y++){for($x=0;$x -lt 70;$x++){
        if($x -ge 23 -and $x -le 48 -and !($x -ge 38 -and $y -lt 53) -and !($x -le 27 -and $y -lt 52)){$out.SetPixel(210+$x,$row*70+$y,$w.GetPixel($x,$row*70+$y))}
    }}}
    # Source contact repeats the first leading foot. Reflect only lower legs to encode the opposite gait half.
    for($col=0;$col -lt 2;$col++){for($row=0;$row -lt 2;$row++){for($y=49;$y -lt 70;$y++){for($x=0;$x -lt 70;$x++){
        $leg=($x -ge 23 -and $x -le 48); if($col -eq 0){$leg=$leg -and !($x -ge 38 -and $y -lt 56) -and !($x -le 27 -and $y -lt 52)}else{$leg=$leg -and !($x -le 32 -and $y -lt 56) -and !($x -ge 45 -and $y -lt 52)}; if($leg){$out.SetPixel(($col+4)*70+$x,($row+2)*70+$y,$front.GetPixel($col*70+69-$x,$row*70+$y))}
    }}}}
    for($row=0;$row -lt 4;$row++){for($col=0;$col -lt 6;$col++){
        $visible=0
        for($y=0;$y -lt 70;$y++){for($x=0;$x -lt 70;$x++){
            $p=$out.GetPixel($col*70+$x,$row*70+$y)
            if($p.A -gt 0){$visible++;if($x -eq 0 -or $x -eq 69 -or $y -eq 0 -or $y -eq 69){throw 'Frame touches edge'}}
            $preserved=($col -lt 3 -or $y -lt 49 -or ($col -ge 4 -and $row -lt 2))
            if($preserved -and $p.ToArgb() -ne $before.GetPixel($col*70+$x,$row*70+$y).ToArgb()){throw 'Preserved pixels changed'}
        }}
        if($visible -eq 0){throw 'Empty frame'}
    }}
    $out.Save((Join-Path $dir 'warrior-fixed.png'),[Drawing.Imaging.ImageFormat]::Png)
    '24 frames/borders verified; E/NE/NW, upper bodies and SW/SE first half unchanged'
}finally{$w.Dispose();$front.Dispose();$out.Dispose();$before.Dispose()}
