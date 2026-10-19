$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $root
$env:JAVA_HOME=(Get-ChildItem "$root/.tools/java" -Directory | Select-Object -First 1).FullName
$maven="$root/.tools/maven/apache-maven-3.9.11/bin/mvn.cmd"
$schemaName='IP_TEST_'+[Guid]::NewGuid().ToString('N').Substring(0,16).ToUpperInvariant()
$schemaCreated=$false
$schemaPassword='Ip'+[Guid]::NewGuid().ToString('N').Substring(0,22)+'9z'
$env:DB_HOST='localhost';$env:DB_PORT='1521';$env:DB_SERVICE='xepdb1';$env:DB_USERNAME=$schemaName;$env:DB_PASSWORD=$schemaPassword;$env:ORACLE_TESTS='true'
$setup=@"
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET VERIFY OFF
ALTER SESSION SET CONTAINER=XEPDB1;
GRANT CREATE SESSION,CREATE TABLE,CREATE SEQUENCE TO $schemaName;
ALTER USER $schemaName QUOTA 20M ON USERS;
CONNECT $schemaName/"$schemaPassword"@//localhost:1521/xepdb1
@database/01_schema.sql
@database/02_seed.sql
@database/03_queries.sql
@database/04_transactions.sql
@database/05_rollback_demo.sql
EXIT
"@
$serverProcess=$null
try {
 $provision=@"
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET VERIFY OFF
ALTER SESSION SET CONTAINER=XEPDB1;
CREATE USER $schemaName IDENTIFIED BY "$schemaPassword";
EXIT
"@
 $provision | sqlplus.exe -L -S '/ as sysdba'
 if($LASTEXITCODE -ne 0){throw 'Temporary schema creation failed; no cleanup of an unowned schema will be attempted'}
 $schemaCreated=$true
 $setup | sqlplus.exe -L -S '/ as sysdba'
 if($LASTEXITCODE -ne 0){throw 'Oracle test schema setup failed'}
 $ErrorActionPreference='Continue'
 & $maven -B -ntp -f backend/pom.xml "-Dmaven.repo.local=$root/.tools/m2" -Pproduction test package
 if($LASTEXITCODE -ne 0){throw 'Oracle integration tests or packaging failed'}
 & $maven -B -ntp -f backend/pom.xml "-Dmaven.repo.local=$root/.tools/m2" exec:exec "-Dexec.executable=$env:JAVA_HOME/bin/java.exe" '-Dexec.args=-classpath %classpath com.inventorypro.JdbcDemo'
 if($LASTEXITCODE -ne 0){throw 'Standalone JDBC demo failed'}
 $ErrorActionPreference='Stop'
 $env:DB_URL='jdbc:oracle:thin:@//localhost:1521/xepdb1';$env:SPRING_PROFILES_ACTIVE='dev';$env:DEV_ADMIN_USERNAME='verify_admin';$env:DEV_ADMIN_PASSWORD='Admin'+[Guid]::NewGuid().ToString('N')+'7z';$env:SESSION_COOKIE_SECURE='false';$env:SERVER_PORT='8081'
 $serverProcess=Start-Process -FilePath "$env:JAVA_HOME/bin/java.exe" -ArgumentList @('-jar',"$root/backend/target/inventorypro-1.0.0.jar",'--debug=false') -WorkingDirectory $root -PassThru -WindowStyle Hidden -RedirectStandardOutput "$root/.tools/oracle-server.log" -RedirectStandardError "$root/.tools/oracle-server-error.log"
 $base='http://localhost:8081/api'
 $ready=$false
 for($attempt=0;$attempt -lt 45;$attempt++){try{$health=Invoke-RestMethod "$base/health";if($health.status -eq 'UP'){$ready=$true;break}}catch{};Start-Sleep -Seconds 1}
 if(-not $ready){throw 'Backend failed to become healthy'}
 $ErrorActionPreference='Continue'
 & python "$root/scripts/qa_http.py"
 if($LASTEXITCODE -ne 0){throw 'Expanded HTTP QA failed'}
 Stop-Process -Id $serverProcess.Id -Force
 $serverProcess.WaitForExit()
 $serverProcess=Start-Process -FilePath "$env:JAVA_HOME/bin/java.exe" -ArgumentList @('-jar',"$root/backend/target/inventorypro-1.0.0.jar",'--debug=false') -WorkingDirectory $root -PassThru -WindowStyle Hidden -RedirectStandardOutput "$root/.tools/oracle-restart.log" -RedirectStandardError "$root/.tools/oracle-restart-error.log"
 $ready=$false
 for($attempt=0;$attempt -lt 45;$attempt++){try{$health=Invoke-RestMethod "$base/health";if($health.status -eq 'UP'){$ready=$true;break}}catch{};Start-Sleep -Seconds 1}
 if(-not $ready){throw 'Restarted backend failed to become healthy'}
 & python "$root/scripts/qa_http.py" --restart-check
 if($LASTEXITCODE -ne 0){throw 'Persistence verification failed'}

} finally {
 if($serverProcess -and -not $serverProcess.HasExited){Stop-Process -Id $serverProcess.Id -Force; $serverProcess.WaitForExit()}
 if($schemaName -notmatch '^IP_TEST_[A-F0-9]{16}$'){throw 'Unexpected cleanup target'}
 if($schemaCreated) {
 @"
WHENEVER SQLERROR EXIT SQL.SQLCODE
ALTER SESSION SET CONTAINER=XEPDB1;
DROP USER $schemaName CASCADE;
EXIT
"@ | sqlplus.exe -L -S '/ as sysdba'
 }
 Remove-Item Env:DB_PASSWORD,Env:DEV_ADMIN_PASSWORD,Env:ORACLE_TESTS -ErrorAction SilentlyContinue
}
