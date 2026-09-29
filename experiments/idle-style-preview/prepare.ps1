$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$root='C:\BlackBerryDev\Workspace\Cave'
$dir=Join-Path $root 'experiments\idle-style-preview'
function Bounds($bmp,$sx,$sy,$w,$h){
    $l=$w;$t=$h;$r=-1;$b=-1
    for($y=0;$y -lt $h;$y++){for($x=0;$x -lt $w;$x++){
        if($bmp.GetPixel($sx+$x,$sy+$y).A -ge 16){$l=[Math]::Min($l,$x);$r=[Math]::Max($r,$x);$t=[Math]::Min($t,$y);$b=[Math]::Max($b,$y)}
    }}
    return @($l,$t,($r-$l+1),($b-$t+1))
}
$preview=New-Object Drawing.Bitmap(960,1400)
$pg=[Drawing.Graphics]::FromImage($preview)
$pg.Clear([Drawing.Color]::FromArgb(32,35,42))
$pg.InterpolationMode=[Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$pg.PixelOffsetMode=[Drawing.Drawing2D.PixelOffsetMode]::Half
$font=New-Object Drawing.Font('Arial',18)
$pg.DrawString('Original idle',[Drawing.Font]$font,[Drawing.Brushes]::White,70,15)
$pg.DrawString('New idle candidate',[Drawing.Font]$font,[Drawing.Brushes]::White,355,15)
$pg.DrawString('Walking reference',[Drawing.Font]$font,[Drawing.Brushes]::White,660,15)
$names=@('warrior','thief','mage','nun')
try {
    for($i=0;$i -lt 4;$i++){
        $name=$names[$i]
        $src=[Drawing.Bitmap]::FromFile((Join-Path $dir ($name+'-generated.png')))
        $old=[Drawing.Bitmap]::FromFile((Join-Path $root ('res\'+$name+'.png')))
        $walk=[Drawing.Bitmap]::FromFile((Join-Path $root ('res\'+$name+'_walk_trial.png')))
        $out=New-Object Drawing.Bitmap(70,70,[Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $g=[Drawing.Graphics]::FromImage($out)
        try{
            $sb=Bounds $src 0 0 $src.Width $src.Height
            $wb=Bounds $walk 350 70 70 70
            $dh=$wb[3];$dw=[int][Math]::Round($sb[2]*$dh/[double]$sb[3])
            if($dw -gt 66){throw 'Candidate too wide'}
            $g.InterpolationMode=[Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
            $g.PixelOffsetMode=[Drawing.Drawing2D.PixelOffsetMode]::Half
            $dest=New-Object Drawing.Rectangle(([int]((70-$dw)/2)),($wb[1]),$dw,$dh)
            $g.DrawImage($src,$dest,$sb[0],$sb[1],$sb[2],$sb[3],[Drawing.GraphicsUnit]::Pixel)
            $out.Save((Join-Path $dir ($name+'-idle-candidate.png')),[Drawing.Imaging.ImageFormat]::Png)
            $top=70+$i*330
            $pg.DrawString($name,$font,[Drawing.Brushes]::White,25,($top+280))
            $dest=New-Object Drawing.Rectangle(25,$top,280,280)
            $pg.DrawImage($old,$dest,0,0,70,70,[Drawing.GraphicsUnit]::Pixel)
            $dest=New-Object Drawing.Rectangle(340,$top,280,280)
            $pg.DrawImage($out,$dest,0,0,70,70,[Drawing.GraphicsUnit]::Pixel)
            $dest=New-Object Drawing.Rectangle(655,$top,280,280)
            $pg.DrawImage($walk,$dest,350,70,70,70,[Drawing.GraphicsUnit]::Pixel)
            Write-Output ($name+': 70x70 candidate, visible height '+$dh)
        }finally{$g.Dispose();$out.Dispose();$src.Dispose();$old.Dispose();$walk.Dispose()}
    }
    $preview.Save((Join-Path $dir 'comparison.png'),[Drawing.Imaging.ImageFormat]::Png)
}finally{$pg.Dispose();$preview.Dispose();$font.Dispose()}
