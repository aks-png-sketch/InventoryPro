param([switch]$InitializeAdmin)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
$localJdk = Get-ChildItem -LiteralPath "$projectRoot/.tools/java" -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
if ($localJdk) { $env:JAVA_HOME = $localJdk.FullName }
$localMaven = "$projectRoot/.tools/maven/apache-maven-3.9.11/bin/mvn.cmd"
$mavenCommand = if (Test-Path -LiteralPath $localMaven) { $localMaven } else { 'mvn.cmd' }
if (-not $env:DB_HOST) { $env:DB_HOST = 'localhost' }
if (-not $env:DB_PORT) { $env:DB_PORT = '1521' }
if (-not $env:DB_SERVICE) { $env:DB_SERVICE = 'XEPDB1' }
if (-not $env:DB_USERNAME) { $env:DB_USERNAME = 'INVENTORY_APP' }
function Read-SecretEnvironment([string]$Label,[string]$Name,[int]$MinimumLength=1,[int]$MaximumBytes=0) {
    while ($true) {
        $secretValue = Read-Host $Label -AsSecureString
        $secretPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secretValue)
        $plainValue = $null
        try {
            $plainValue = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($secretPointer)
            if ([string]::IsNullOrWhiteSpace($plainValue) -or $plainValue.Length -lt $MinimumLength) {
                Write-Host "Enter at least $MinimumLength characters. Please try again." -ForegroundColor Yellow
                continue
            }
            if ($MaximumBytes -gt 0 -and [Text.Encoding]::UTF8.GetByteCount($plainValue) -gt $MaximumBytes) {
                Write-Host "Password exceeds $MaximumBytes UTF-8 bytes. Please try a shorter password." -ForegroundColor Yellow
                continue
            }
            [Environment]::SetEnvironmentVariable($Name,$plainValue,'Process')
            break
        } finally {
            $plainValue = $null
            [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($secretPointer)
            $secretValue.Dispose()
        }
    }
}
try {
if (-not $env:DB_PASSWORD) { Read-SecretEnvironment 'Application DB password' 'DB_PASSWORD' }
# HTTP loopback development only. Production defaults to Secure cookies.
$env:SESSION_COOKIE_SECURE = 'false'
if ($InitializeAdmin) {
    $env:SPRING_PROFILES_ACTIVE = 'dev'
    while ($env:DEV_ADMIN_USERNAME -cnotmatch '^[A-Za-z0-9_.-]{3,50}$') {
        $env:DEV_ADMIN_USERNAME = Read-Host 'New development admin username (3-50 letters, digits, dots, underscores or hyphens)'
    }
    Read-SecretEnvironment 'New admin password (12+ characters, max 72 UTF-8 bytes)' 'DEV_ADMIN_PASSWORD' 12 72
}
    & $mavenCommand -f backend/pom.xml "-Dmaven.repo.local=$projectRoot/.tools/m2" spring-boot:run '-Dspring-boot.run.arguments=--debug=false'
    if ($LASTEXITCODE -ne 0) { throw 'Backend exited with an error. Read the log above.' }
} finally {
    Remove-Item Env:DEV_ADMIN_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:DB_PASSWORD -ErrorAction SilentlyContinue
}
