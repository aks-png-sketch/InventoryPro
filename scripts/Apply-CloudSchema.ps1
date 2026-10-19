param([string]$JdbcUrl)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
$localJdk = Get-ChildItem -LiteralPath "$projectRoot/.tools/java" -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
if ($localJdk) { $env:JAVA_HOME = $localJdk.FullName }
if ($env:JAVA_HOME) { $env:Path = "$env:JAVA_HOME/bin;$env:Path" }
$localMaven = "$projectRoot/.tools/maven/apache-maven-3.9.11/bin/mvn.cmd"
$maven = if (Test-Path -LiteralPath $localMaven) { $localMaven } else { 'mvn.cmd' }
if (-not $JdbcUrl) { $JdbcUrl = Read-Host 'Paste the cloud Oracle walletless TLS JDBC URL' }
if (-not $JdbcUrl) { throw 'A cloud JDBC URL is required.' }
$env:DB_URL = $JdbcUrl
$env:DB_USERNAME = 'INVENTORY_APP'
$env:CLOUD_ADMIN_USERNAME = Read-Host 'New cloud admin username (3-50 letters, digits, dots, underscores or hyphens)'
$dbSecure = Read-Host 'Cloud INVENTORY_APP password' -AsSecureString
$adminSecure = Read-Host 'New cloud admin password (12+ characters, max 72 UTF-8 bytes)' -AsSecureString
$dbPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($dbSecure)
$adminPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($adminSecure)
try {
    [Environment]::SetEnvironmentVariable('DB_PASSWORD',[Runtime.InteropServices.Marshal]::PtrToStringBSTR($dbPointer),'Process')
    [Environment]::SetEnvironmentVariable('CLOUD_ADMIN_PASSWORD',[Runtime.InteropServices.Marshal]::PtrToStringBSTR($adminPointer),'Process')
    & $maven -B -ntp -f backend/pom.xml "-Dmaven.repo.local=$projectRoot/.tools/m2" compile exec:exec '-Dexec.executable=java' '-Dexec.args=-classpath %classpath com.inventorypro.CloudSchemaInitializer'
    if ($LASTEXITCODE -ne 0) { throw 'Cloud Oracle schema setup failed. Inspect the error; do not rerun if any tables were created.' }
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($dbPointer)
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($adminPointer)
    $dbSecure.Dispose()
    $adminSecure.Dispose()
    Remove-Item Env:DB_PASSWORD,Env:DB_URL,Env:DB_USERNAME,Env:CLOUD_ADMIN_USERNAME,Env:CLOUD_ADMIN_PASSWORD -ErrorAction SilentlyContinue
}
