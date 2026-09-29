$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$root='C:\BlackBerryDev\Workspace\Cave'
$dir=Join-Path $root 'experiments\walk-idle-match'
$names=@('warrior','thief','mage','nun')
$preview=New-Object Drawing.Bitmap(1680,1250)
$pg=[Drawing.Graphics]::FromImage($preview)
$pg.Clear([Drawing.Color]::FromArgb(32,35,42))
$pg.InterpolationMode=[Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$pg.PixelOffsetMode=[Drawing.Drawing2D.PixelOffsetMode]::Half
$font=New-Object Drawing.Font('Arial',16)
$labels=@('Original idle','Before','New contact A','New passing A','New contact B','New passing B')
for($i=0;$i -lt 6;$i++){$pg.DrawString($labels[$i],$font,[Drawing.Brushes]::White,($i*280+15),12)}
try{
foreach($name in $names){
    $source=[Drawing.Bitmap]::FromFile((Join-Path $dir ($name+'-generated.png')))
    $idle=[Drawing.Bitmap]::FromFile((Join-Path $root ('res\'+$name+'.png')))
    $before=[Drawing.Bitmap]::FromFile((Join-Path $dir ('before\'+$name+'_walk_trial.png')))
    $output=New-Object Drawing.Bitmap(420,280,[Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g=[Drawing.Graphics]::FromImage($output)
    try{
        if($source.Width%6 -ne 0){throw 'Width must divide into six columns'}
        $bands=@();$start=-1
        for($y=0;$y -lt $source.Height;$y++){
            $count=0
            for($x=0;$x -lt $source.Width;$x++){if($source.GetPixel($x,$y).A -ge 128){$count++}}
            if($count -gt 15){if($start -lt 0){$start=$y}}
            elseif($start -ge 0){$bands+=,@($start,($y-1));$start=-1}
        }
        if($start -ge 0){$bands+=,@($start,($source.Height-1))}
        if($bands.Count -ne 4){throw ($name+': expected four rows; found '+$bands.Count)}
        $minY=70;$maxY=-1
        for($y=0;$y -lt 70;$y++){for($x=0;$x -lt 70;$x++){
            if($idle.GetPixel($x,$y).A -ge 16){$minY=[Math]::Min($minY,$y);$maxY=[Math]::Max($maxY,$y)}
        }}
        $maxHeight=0
        foreach($band in $bands){$maxHeight=[Math]::Max($maxHeight,($band[1]-$band[0]+1))}
        $cellW=[int]($source.Width/6)
        $scale=[Math]::Min((($maxY-$minY+1)/[double]$maxHeight),(66.0/$cellW))
        $g.CompositingMode=[Drawing.Drawing2D.CompositingMode]::SourceCopy
        $g.InterpolationMode=[Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
        $g.PixelOffsetMode=[Drawing.Drawing2D.PixelOffsetMode]::Half
        for($row=0;$row -lt 4;$row++){
            $top=[Math]::Max(0,($bands[$row][0]-3));$bottom=[Math]::Min(($source.Height-1),($bands[$row][1]+3))
            $sh=$bottom-$top+1;$dh=[int][Math]::Round($sh*$scale);$dw=[int][Math]::Round($cellW*$scale)
            for($col=0;$col -lt 6;$col++){
                $dest=New-Object Drawing.Rectangle(([int]($col*70+(70-$dw)/2)),([int]($row*70+$maxY+1+3*$scale-$dh)),$dw,$dh)
                $g.DrawImage($source,$dest,($col*$cellW),$top,$cellW,$sh,[Drawing.GraphicsUnit]::Pixel)
            }
        }
        for($row=0;$row -lt 4;$row++){for($col=0;$col -lt 6;$col++){
            $visible=0
            for($y=0;$y -lt 70;$y++){for($x=0;$x -lt 70;$x++){
                if($output.GetPixel($col*70+$x,$row*70+$y).A -gt 0){
                    $visible++;if($x -eq 0 -or $x -eq 69 -or $y -eq 0 -or $y -eq 69){throw ($name+': frame touches edge')}
                }
            }}
            if($visible -eq 0){throw ($name+': empty frame')}
        }}
        $output.Save((Join-Path $dir ($name+'-walk-refined.png')),[Drawing.Imaging.ImageFormat]::Png)
        if($name -eq 'nun' -and (Test-Path (Join-Path $dir 'nun-rear-generated.png'))){
            & (Join-Path $dir 'prepare-nun-rear.ps1')
            $g.Dispose();$g=$null;$output.Dispose()
            $output=[Drawing.Bitmap]::FromFile((Join-Path $dir 'nun-walk-refined.png'))
        }
        $i=[Array]::IndexOf($names,$name);$py=50+$i*300
        $pg.DrawImage($idle,(New-Object Drawing.Rectangle(0,$py,280,280)),0,0,70,70,[Drawing.GraphicsUnit]::Pixel)
        $pg.DrawImage($before,(New-Object Drawing.Rectangle(280,$py,280,280)),350,0,70,70,[Drawing.GraphicsUnit]::Pixel)
        for($r=0;$r -lt 4;$r++){
            $pg.DrawImage($output,(New-Object Drawing.Rectangle((($r+2)*280),$py,280,280)),350,($r*70),70,70,[Drawing.GraphicsUnit]::Pixel)
        }
        $pg.DrawString($name,$font,[Drawing.Brushes]::White,5,($py+270))
        Write-Output ($name+': 24 frames and transparent borders verified; idle baseline '+$maxY)
    }finally{if($g){$g.Dispose()};$output.Dispose();$source.Dispose();$idle.Dispose();$before.Dispose()}
}
$preview.Save((Join-Path $dir 'comparison.png'),[Drawing.Imaging.ImageFormat]::Png)
}finally{$pg.Dispose();$preview.Dispose();$font.Dispose()}
