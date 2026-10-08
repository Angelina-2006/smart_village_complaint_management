$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (-not $env:JAVA_HOME) {
    $bundledJdk = 'C:\Program Files\Android\Android Studio\jbr'
    if (Test-Path "$bundledJdk\bin\javac.exe") { $env:JAVA_HOME = $bundledJdk }
}
$mavenCommand = Get-Command mvn -ErrorAction SilentlyContinue
$localMaven = Join-Path $PSScriptRoot '.tools\apache-maven-3.9.9\bin\mvn.cmd'
if ($mavenCommand) { $mavenPath = $mavenCommand.Source }
elseif (Test-Path $localMaven) { $mavenPath = $localMaven }
else { throw 'Install Maven 3.9+ and JDK 17+, then run this script again.' }
Write-Host 'Starting the LOCAL DEMO with H2, not MySQL.'
Write-Host 'Open http://localhost:8080. Administrator: admin@village.local / LocalPreview123!'
Write-Host 'Create a villager account through the registration page. Press Ctrl+C to stop.'
& $mavenPath -B -ntp '-Dmaven.repo.local=.tools/repository' test-compile exec:java '-Dexec.mainClass=org.smartvillage.DemoServer' '-Dexec.classpathScope=test'
exit $LASTEXITCODE
