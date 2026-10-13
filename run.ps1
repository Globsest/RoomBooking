param([Parameter(ValueFromRemainingArguments=$true)][string[]]$AppArgs)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$jdkCandidates = @($env:JAVA_HOME, 'C:\Program Files\Java\jdk-17')
$jdk = $jdkCandidates | Where-Object { $_ -and (Test-Path (Join-Path $_ 'bin\javac.exe')) } | Select-Object -First 1
if ($jdk) {
    $compiler = Join-Path $jdk 'bin\javac.exe'
    $runtime = Join-Path $jdk 'bin\java.exe'
} else {
    $compiler = (Get-Command javac -ErrorAction Stop).Source
    $runtime = (Get-Command java -ErrorAction Stop).Source
}
New-Item -ItemType Directory -Path 'out' -Force | Out-Null
$sources = @(Get-ChildItem -LiteralPath 'src' -Filter '*.java' | ForEach-Object { $_.FullName })
& $compiler -encoding UTF-8 --release 17 -d out @sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $runtime '-Dfile.encoding=UTF-8' -cp out RoomBooking @AppArgs
exit $LASTEXITCODE
