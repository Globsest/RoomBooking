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
New-Item -ItemType Directory -Path 'out\junit-main','out\junit-tests','out\test-temp' -Force | Out-Null
$junit = Join-Path $PSScriptRoot 'lib\junit-platform-console-standalone-1.11.4.jar'
if (-not (Test-Path -LiteralPath $junit)) { throw 'JUnit JAR missing. Restore lib or use Maven: mvn test.' }
$sources = @(Get-ChildItem -LiteralPath 'src' -Filter '*.java' | ForEach-Object { $_.FullName })
& $compiler -encoding UTF-8 --release 17 -d out\junit-main @sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$tests = @(Get-ChildItem -LiteralPath 'tests' -Filter '*.java' | ForEach-Object { $_.FullName })
& $compiler -encoding UTF-8 --release 17 -cp "$junit;out\junit-main" -d out\junit-tests @tests
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$tempDirectory = Join-Path $PSScriptRoot 'out\test-temp'
& $runtime '-Dfile.encoding=UTF-8' "-Djava.io.tmpdir=$tempDirectory" -jar $junit execute --class-path 'out\junit-main;out\junit-tests' --scan-class-path --fail-if-no-tests --disable-ansi-colors --reports-dir out\junit-reports
exit $LASTEXITCODE
