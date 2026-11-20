# Creates "dayener dls" shortcuts (Desktop + Start menu) with the launcher icon.
# Unlike a .bat file, these shortcuts can be pinned to the taskbar / Start.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$root = $PSScriptRoot
$dist = Join-Path $root 'dist'
$jar = Join-Path $dist 'dayener-dls-launcher.jar'
$ico = Join-Path $dist 'dayener-dls.ico'
$javaw = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot\bin\javaw.exe'
if (-not (Test-Path $javaw)) { $javaw = (Get-Command javaw.exe -ErrorAction Stop).Source }
if (-not (Test-Path $jar)) { throw "Launcher not built yet: $jar (run build.ps1)" }

# ---- icon: logo.png, middle 80 % (same crop as in the launcher), several PNG sizes in one .ico ----
$src = [System.Drawing.Image]::FromFile((Join-Path $root 'resources\logo.png'))
$side = [int]([Math]::Min($src.Width, $src.Height) * 0.8)
$ox = [int](($src.Width - $side) / 2); $oy = [int](($src.Height - $side) / 2)
$sizes = 16, 24, 32, 48, 64, 128, 256
$pngs = @()
foreach ($s in $sizes) {
	$bmp = New-Object System.Drawing.Bitmap $s, $s
	$g = [System.Drawing.Graphics]::FromImage($bmp)
	$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
	$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
	$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
	# Rounded corners like in the launcher.
	$r = [Math]::Max(2, [int]($s * 0.25))
	$path = New-Object System.Drawing.Drawing2D.GraphicsPath
	$path.AddArc(0, 0, $r * 2, $r * 2, 180, 90); $path.AddArc($s - $r * 2, 0, $r * 2, $r * 2, 270, 90)
	$path.AddArc($s - $r * 2, $s - $r * 2, $r * 2, $r * 2, 0, 90); $path.AddArc(0, $s - $r * 2, $r * 2, $r * 2, 90, 90)
	$path.CloseFigure()
	$g.SetClip($path)
	$g.DrawImage($src, (New-Object System.Drawing.Rectangle 0, 0, $s, $s), $ox, $oy, $side, $side, [System.Drawing.GraphicsUnit]::Pixel)
	$g.Dispose()
	$ms = New-Object System.IO.MemoryStream
	$bmp.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
	$bmp.Dispose()
	$pngs += , $ms.ToArray()
}
$src.Dispose()

$out = New-Object System.IO.MemoryStream
$w = New-Object System.IO.BinaryWriter $out
$w.Write([UInt16]0); $w.Write([UInt16]1); $w.Write([UInt16]$sizes.Count)
$offset = 6 + 16 * $sizes.Count
for ($i = 0; $i -lt $sizes.Count; $i++) {
	$s = $sizes[$i]; $b = if ($s -ge 256) { 0 } else { $s }
	$w.Write([byte]$b); $w.Write([byte]$b); $w.Write([byte]0); $w.Write([byte]0)
	$w.Write([UInt16]1); $w.Write([UInt16]32); $w.Write([UInt32]$pngs[$i].Length); $w.Write([UInt32]$offset)
	$offset += $pngs[$i].Length
}
foreach ($p in $pngs) { $w.Write($p) }
[System.IO.File]::WriteAllBytes($ico, $out.ToArray())

# ---- shortcuts ----
$shell = New-Object -ComObject WScript.Shell
$targets = @(
	(Join-Path ([Environment]::GetFolderPath('Desktop')) 'dayener dls.lnk'),
	(Join-Path ([Environment]::GetFolderPath('Programs')) 'dayener dls.lnk')
)
foreach ($lnkPath in $targets) {
	$lnk = $shell.CreateShortcut($lnkPath)
	$lnk.TargetPath = $javaw
	$lnk.Arguments = "-jar `"$jar`""
	$lnk.WorkingDirectory = $dist
	$lnk.IconLocation = "$ico,0"
	$lnk.Description = 'dayener dls launcher'
	$lnk.Save()
	Write-Host "Shortcut: $lnkPath"
}
