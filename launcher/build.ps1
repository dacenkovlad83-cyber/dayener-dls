# Builds the dayener dls launcher into launcher\dist\dayener-dls-launcher.jar.
# The mod jar from ..\build\libs (built with gradlew build) is packed inside the launcher.
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$jdk = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { 'C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot' }
$javac = Join-Path $jdk 'bin\javac.exe'
$jar = Join-Path $jdk 'bin\jar.exe'

$out = Join-Path $root 'out'
$dist = Join-Path $root 'dist'
if (Test-Path $out) { Remove-Item $out -Recurse -Force -Confirm:$false }
New-Item -ItemType Directory -Force $out, $dist, (Join-Path $out 'mod') | Out-Null

$mod = Join-Path $root '..\build\libs\dayener-dls-1.0.0.jar'
if (-not (Test-Path $mod)) { throw "Mod jar not found: $mod (run gradlew build first)" }
Copy-Item $mod (Join-Path $out 'mod\dayener-dls.jar')

$res = Join-Path $root 'resources'
if (Test-Path $res) { Copy-Item (Join-Path $res '*') $out -Recurse -Force }

$sources = Get-ChildItem (Join-Path $root 'src') -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& $javac --release 25 -encoding UTF-8 -d $out @sources
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }

$target = Join-Path $dist 'dayener-dls-launcher.jar'
& $jar --create --file $target --main-class dls.launcher.Main -C $out .
if ($LASTEXITCODE -ne 0) { throw 'jar failed' }
Write-Host "Built $target"
