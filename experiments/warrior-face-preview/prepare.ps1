$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$dir='C:\BlackBerryDev\Workspace\Cave\experiments\warrior-face-preview'
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
function SkinBounds($bmp,$col,$row){
    $l=70;$t=70;$r=-1;$b=-1
    for($y=20;$y -lt 36;$y++){for($x=20;$x -lt 49;$x++){
        $p=$bmp.GetPixel($col*70+$x,$row*70+$y)
        if($p.A -ge 128 -and $p.R -gt 175 -and $p.G -gt 100 -and $p.B -gt 65 -and $p.R -gt $p.G*1.1 -and $p.G -gt $p.B*1.1){
            $l=[Math]::Min($l,$x);$r=[Math]::Max($r,$x);$t=[Math]::Min($t,$y);$b=[Math]::Max($b,$y)
        }
    }}
    if($r -lt 0){return $null}
    return @($l,$t,($r-$l+1),($b-$t+1))
}
$out=$before.Clone((New-Object Drawing.Rectangle(0,0,420,280)),[Drawing.Imaging.PixelFormat]::Format32bppArgb)
try{
    $changed=0
    for($row=0;$row -lt 4;$row++){for($col=0;$col -lt 6;$col++){
        # Surface gray details only: preserve existing helmet silhouette and alpha.
        for($y=12;$y -lt 28;$y++){for($x=20;$x -lt 49;$x++){
            $old=$before.GetPixel($col*70+$x,$row*70+$y);$new=$paint.GetPixel($col*70+$x,$row*70+$y)
            $oldMetal=($old.A -gt 0 -and $old.B -gt 70 -and [Math]::Abs($old.R-$old.B) -lt 30 -and [Math]::Abs($old.G-$old.B) -lt 30)
            $newMetal=($new.A -ge 128 -and $new.B -gt 70 -and [Math]::Abs($new.R-$new.B) -lt 30 -and [Math]::Abs($new.G-$new.B) -lt 30)
            if($oldMetal -and $newMetal){$out.SetPixel($col*70+$x,$row*70+$y,[Drawing.Color]::FromArgb($old.A,$new.R,$new.G,$new.B))}
        }}
        if($col -eq 1 -or $col -eq 2){continue}
        $a=SkinBounds $before $col $row;$b=SkinBounds $paint $col $row
        if(!$a -or !$b){throw 'Face registration failed'}
        for($y=0;$y -lt $a[3];$y++){for($x=0;$x -lt $a[2];$x++){
            $sx=[int][Math]::Floor(($x+0.5)*$b[2]/$a[2]);$sy=[int][Math]::Floor(($y+0.5)*$b[3]/$a[3])
            $old=$before.GetPixel($col*70+$a[0]+$x,$row*70+$a[1]+$y)
            $new=$paint.GetPixel($col*70+$a[0]+$x,$row*70+$a[1]+$y)
            if($old.A -gt 0 -and $new.A -ge 128){$out.SetPixel($col*70+$a[0]+$x,$row*70+$a[1]+$y,[Drawing.Color]::FromArgb($old.A,$new.R,$new.G,$new.B))}
        }}
    }}
    for($y=0;$y -lt 280;$y++){for($x=0;$x -lt 420;$x++){
        $old=$before.GetPixel($x,$y);$new=$out.GetPixel($x,$y)
        if($old.A -ne $new.A){throw 'Silhouette changed'}
        if($old.ToArgb() -ne $new.ToArgb()){
            $changed++
            if($y%70 -ge 36){throw 'Body or gait pixels changed'}
        }
    }}
    $out.Save((Join-Path $dir 'warrior-candidate.png'),[Drawing.Imaging.ImageFormat]::Png)
    Write-Output ($changed.ToString()+' head pixels changed; full alpha silhouette and all body/gait pixels unchanged')
}finally{$out.Dispose();$paint.Dispose();$before.Dispose()}
