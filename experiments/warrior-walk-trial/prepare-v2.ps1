Add-Type -AssemblyName System.Drawing
$root='C:\BlackBerryDev\Workspace\Cave'
$source=[Drawing.Bitmap]::FromFile((Join-Path $root 'experiments\warrior-walk-trial\generated-atlas-v2.png'))
$output=New-Object Drawing.Bitmap(420,280,[Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g=[Drawing.Graphics]::FromImage($output)
$bands=@(@(52,273),@(290,508),@(529,753),@(771,992))
try{
    $g.CompositingMode=[Drawing.Drawing2D.CompositingMode]::SourceCopy
    $g.InterpolationMode=[Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $g.PixelOffsetMode=[Drawing.Drawing2D.PixelOffsetMode]::Half
    for($row=0;$row -lt 4;$row++){
        $h=$bands[$row][1]-$bands[$row][0]+1
        for($col=0;$col -lt 6;$col++){
            $dest=New-Object Drawing.Rectangle(($col*70+4),($row*70+64-$h*0.24),61,($h*0.24))
            $g.DrawImage($source,$dest,($col*256),$bands[$row][0],256,$h,[Drawing.GraphicsUnit]::Pixel)
        }
    }
    $output.Save((Join-Path $root 'res\warrior_walk_trial.png'),[Drawing.Imaging.ImageFormat]::Png)
}finally{$g.Dispose();$output.Dispose();$source.Dispose()}
$bmp=[Drawing.Bitmap]::FromFile((Join-Path $root 'res\warrior_walk_trial.png'))
try{
    for($col=0;$col -lt 6;$col++){
        $widths=@()
        for($row=0;$row -lt 4;$row++){
            $visible=0;$edge=0;$min=70;$max=-1
            for($y=0;$y -lt 70;$y++){for($x=0;$x -lt 70;$x++){
                if($bmp.GetPixel($col*70+$x,$row*70+$y).A -gt 0){
                    $visible++
                    if($x -eq 0 -or $x -eq 69 -or $y -eq 0 -or $y -eq 69){$edge++}
                    if($y -ge 50){$min=[Math]::Min($min,$x);$max=[Math]::Max($max,$x)}
                }
            }}
            if($visible -eq 0 -or $edge -gt 0){throw "Invalid frame $col,$row"}
            $widths+=($max-$min+1)
        }
        Write-Output ('Direction '+$col+' boot spans: '+($widths -join ', '))
    }
    '24 populated frames with transparent padding verified'
}finally{$bmp.Dispose()}