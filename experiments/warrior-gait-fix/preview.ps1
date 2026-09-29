$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$dir='C:\BlackBerryDev\Workspace\Cave\experiments\warrior-gait-fix'
$src=[Drawing.Bitmap]::FromFile((Join-Path $dir 'warrior-fixed.png'))
$font=New-Object Drawing.Font('Arial',16)
try{
    for($frame=0;$frame -lt 4;$frame++){
        $bmp=New-Object Drawing.Bitmap(840,310)
        $g=[Drawing.Graphics]::FromImage($bmp)
        try{
            $g.Clear([Drawing.Color]::FromArgb(32,35,42))
            $g.InterpolationMode=[Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
            $g.PixelOffsetMode=[Drawing.Drawing2D.PixelOffsetMode]::Half
            for($col=0;$col -lt 3;$col++){
                $g.DrawString(@('W','SW','SE')[$col],$font,[Drawing.Brushes]::White,($col*280+10),5)
                $g.DrawImage($src,(New-Object Drawing.Rectangle(($col*280),30,280,280)),(($col+3)*70),($frame*70),70,70,[Drawing.GraphicsUnit]::Pixel)
            }
            $bmp.Save((Join-Path $dir ('frame-'+$frame+'.png')),[Drawing.Imaging.ImageFormat]::Png)
        }finally{$g.Dispose();$bmp.Dispose()}
    }
}finally{$font.Dispose();$src.Dispose()}
& ffmpeg -y -loglevel error -framerate 10 -i (Join-Path $dir 'frame-%d.png') -filter_complex 'split[a][b];[a]palettegen[p];[b][p]paletteuse' -loop 0 (Join-Path $dir 'walk.gif')
if($LASTEXITCODE -ne 0){throw 'GIF preview generation failed'}
