$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
$localJdk = Get-ChildItem -LiteralPath "$projectRoot/.tools/java" -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
if ($localJdk) { $env:JAVA_HOME = $localJdk.FullName }
$localMaven = "$projectRoot/.tools/maven/apache-maven-3.9.11/bin/mvn.cmd"
$mavenCommand = if (Test-Path -LiteralPath $localMaven) { $localMaven } else { 'mvn.cmd' }
& $mavenCommand -B -ntp -f backend/pom.xml "-Dmaven.repo.local=$projectRoot/.tools/m2" test
exit $LASTEXITCODE
