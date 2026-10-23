# Exports rewatt-user-manual.html to an A5 PDF with headless Microsoft Edge.
#
# Pages marked data-flatten (soft shadows, filters, clip-paths) are first captured as
# ~290 dpi images and embedded as pictures: phone PDF viewers often draw those effects
# as grey boxes, while a picture looks the same everywhere. All other pages stay vector.
Add-Type -AssemblyName System.Drawing
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$edge = @("${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe", "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe") | Where-Object { Test-Path $_ } | Select-Object -First 1
$source = Join-Path $here 'rewatt-user-manual.html'
$html = "file:///" + ($source -replace '\\', '/' -replace ' ', '%20')
$pdf = Join-Path $here 'reWatt-User-Manual.pdf'

# 1) capture each data-flatten page (1-based page number = order of <section class="page">)
$sections = [regex]::Matches([IO.File]::ReadAllText($source), '<section class="page[^"]*"[^>]*>')
$scale = 3
for ($i = 0; $i -lt $sections.Count; $i++) {
    $m = [regex]::Match($sections[$i].Value, 'data-flatten="([^"]+)"')
    if (-not $m.Success) { continue }
    $out = Join-Path $here ($m.Groups[1].Value -replace '/', '\')
    New-Item -ItemType Directory -Force (Split-Path $out) | Out-Null
    $shot = "$out.raw.png"
    & $edge --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=$scale `
        --window-size=560,794 --virtual-time-budget=5000 --screenshot="$shot" "$html#only=$($i + 1)" 2>$null | Out-Null
    # crop to exactly 148 x 210 mm (559.37 x 793.70 CSS px)
    $raw = [System.Drawing.Image]::FromFile($shot)
    $w = [int][math]::Floor(559.37 * $scale); $h = [int][math]::Floor(793.70 * $scale)
    $bmp = New-Object System.Drawing.Bitmap $w, $h
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.DrawImage($raw, [System.Drawing.Rectangle]::new(0, 0, $w, $h), [System.Drawing.Rectangle]::new(0, 0, $w, $h), [System.Drawing.GraphicsUnit]::Pixel)
    $g.Dispose(); $raw.Dispose()
    $bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose()
    Remove-Item $shot
    "flattened page $($i + 1) -> $($m.Groups[1].Value)"
}

# 2) print, with flattened pages swapped for their images
& $edge --headless=new --disable-gpu --no-pdf-header-footer --virtual-time-budget=5000 --print-to-pdf="$pdf" "$html#flat" 2>$null | Out-Null
Start-Sleep -Seconds 1
Get-Item $pdf | Select-Object FullName, Length, LastWriteTime
