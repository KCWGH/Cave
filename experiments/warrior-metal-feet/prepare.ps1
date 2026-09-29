$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$dir='C:\BlackBerryDev\Workspace\Cave\experiments\warrior-metal-feet'
$src=[Drawing.Bitmap]::FromFile((Join-Path $dir 'generated.png'))
$before=[Drawing.Bitmap]::FromFile((Join-Path $dir 'warrior-before.png'))
$paint=New-Object Drawing.Bitmap(420,280,[Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g=[Drawing.Graphics]::FromImage($paint)
$bands=@(@(52,273),@(290,508),@(529,753),@(771,992))
try{
    $g.CompositingMode=[Drawing.Drawing2D.CompositingMode]::SourceCopy
    $g.InterpolationMode=[Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $g.PixelOffsetMode=[Drawing.Drawing2D.PixelOffsetMode]::Half
    for($row=0;$row -lt 4;$row++){
        $h=$bands[$row][1]-$bands[$row][0]+1
        for($col=0;$col -lt 6;$col++){
            $dest=New-Object Drawing.Rectangle(($col*70+4),($row*70+64-$h*0.24),61,($h*0.24))
            $g.DrawImage($src,$dest,($col*256),$bands[$row][0],256,$h,[Drawing.GraphicsUnit]::Pixel)
        }
    }
}finally{$g.Dispose();$src.Dispose()}
$out=$before.Clone((New-Object Drawing.Rectangle(0,0,420,280)),[Drawing.Imaging.PixelFormat]::Format32bppArgb)
$allMask=[bool[]]::new(420*280)
try{
    $changed=0
    for($row=0;$row -lt 4;$row++){for($col=0;$col -lt 6;$col++){
        $eligible=[bool[]]::new(4900);$mask=[bool[]]::new(4900)
        $queue=New-Object Collections.Queue
        for($y=43;$y -lt 70;$y++){for($x=0;$x -lt 70;$x++){
            $p=$before.GetPixel($col*70+$x,$row*70+$y)
            $brown=($p.A -gt 0 -and $p.R -gt 25 -and $p.G -ge 10 -and $p.R -gt $p.G*1.4 -and $p.R -gt $p.B*1.8 -and $p.G -gt $p.B*1.15)
            $eligible[$y*70+$x]=$brown
            if($brown -and $y -ge 53){$mask[$y*70+$x]=$true;$queue.Enqueue(($y*70+$x))}
        }}
        while($queue.Count -gt 0){
            $i=[int]$queue.Dequeue();$x=$i%70;$y=[int][Math]::Floor($i/70.0)
            for($dy=-1;$dy -le 1;$dy++){for($dx=-1;$dx -le 1;$dx++){
                $nx=$x+$dx;$ny=$y+$dy
                if($nx -lt 0 -or $nx -ge 70 -or $ny -lt 43 -or $ny -ge 70){continue}
                $j=$ny*70+$nx
                if($eligible[$j] -and !$mask[$j]){$mask[$j]=$true;$queue.Enqueue($j)}
            }}
        }
        $count=0
        for($y=43;$y -lt 70;$y++){for($x=0;$x -lt 70;$x++){
            if(!$mask[$y*70+$x]){continue}
            $gx=$col*70+$x;$gy=$row*70+$y;$old=$before.GetPixel($gx,$gy);$new=$paint.GetPixel($gx,$gy)
            $metal=($new.A -ge 128 -and $new.B -ge 30 -and [Math]::Abs($new.R-$new.B) -lt 40 -and [Math]::Abs($new.G-$new.B) -lt 40)
            if(!$metal){
                # Registration fallback: retain the source pixel's shade through a steel-color ramp.
                $v=[int][Math]::Min(220,[Math]::Round(18+$old.R*0.8))
                $new=[Drawing.Color]::FromArgb($v,([Math]::Min(255,$v+7)),([Math]::Min(255,$v+18)))
            }
            $out.SetPixel($gx,$gy,[Drawing.Color]::FromArgb($old.A,$new.R,$new.G,$new.B))
            $allMask[$gy*420+$gx]=$true;$count++;$changed++
        }}
        if($count -lt 10){throw 'Boot mask missing'}
        Write-Output ('Frame '+$col+','+$row+': '+$count+' boot-color pixels')
    }}
    for($y=0;$y -lt 280;$y++){for($x=0;$x -lt 420;$x++){
        $old=$before.GetPixel($x,$y);$new=$out.GetPixel($x,$y)
        if($old.A -ne $new.A){throw 'Pose/silhouette alpha changed'}
        if(!$allMask[$y*420+$x] -and $old.ToArgb() -ne $new.ToArgb()){throw 'Non-boot pixels changed'}
    }}
    $out.Save((Join-Path $dir 'warrior-metal-feet.png'),[Drawing.Imaging.ImageFormat]::Png)
    Write-Output ($changed.ToString()+' boot RGB pixels updated; all alpha and non-boot pixels identical')
}finally{$out.Dispose();$paint.Dispose();$before.Dispose()}
