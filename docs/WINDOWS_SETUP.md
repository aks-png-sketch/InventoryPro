# Windows setup and troubleshooting

## Software installation

Install Node.js LTS from https://nodejs.org/, a JDK **21** from https://adoptium.net/temurin/releases/?version=21, Maven 3.9 from https://maven.apache.org/download.cgi and Oracle Database 21c XE or another supported local Oracle version. SQL*Plus ships with the database installation. Install Git from https://git-scm.com/download/win if needed for version control. Reopen PowerShell after installation.

For a system JDK, set Windows **User environment variables**: `JAVA_HOME` to the JDK directory (not `bin`), add `%JAVA_HOME%\bin` to your user PATH. Extract Maven to a directory such as `C:\Tools\apache-maven-3.9.11` and add its `bin` directory to your user PATH. Use the Environment Variables editor so you do not accidentally replace existing PATH entries.

Verify in a new terminal:

```powershell
java -version
javac -version
mvn.cmd -version
node --version
npm.cmd --version
sqlplus -v
```

Java must report 21, Maven must report that same Java installation. Vite 7 requires Node 20.19+ or 22.12+; Node 24 in this workspace is compatible. The helpers can use optional portable tools under `.tools/` if those have been installed locally; this ignored directory is not distributed in Git.

## Discover Oracle service and listener

In PowerShell:

```powershell
lsnrctl status
sqlplus /nolog
```

In SQL*Plus, connect as an administrator (the password is prompted locally):

```sql
CONNECT SYSTEM
SHOW CON_NAME;
SELECT name,open_mode FROM v$pdbs;
SELECT name FROM v$services ORDER BY name;
ALTER SESSION SET CONTAINER=XEPDB1;
SHOW CON_NAME;
SELECT SYS_CONTEXT('USERENV','SERVICE_NAME') FROM dual;
```

Use the PDB service reported by the listener, not the root `XE` service. On this machine the listener confirmed TCP 1521 and service `xepdb1`; `XEPDB1` is READ WRITE. If your PDB has a different name, replace it in the SQL and DB_SERVICE variable. Administrators can open a stopped PDB using `ALTER PLUGGABLE DATABASE XEPDB1 OPEN;` from the root, only if appropriate for their installation.

Follow the exact provisioning and startup commands in [README](../README.md). The app user needs `CREATE SESSION`, `CREATE TABLE`, `CREATE SEQUENCE`, and a bounded quota on the USERS tablespace. It does not need DBA, SYS, SYSTEM, UNLIMITED TABLESPACE or access to other schemas. If your administrator uses another tablespace, adjust the quota clause in `00_create_user.sql`.

## Start without helper scripts (after installing Java/Maven)

In PowerShell terminal A at the root:

```powershell
$env:DB_HOST='localhost'
$env:DB_PORT='1521'
$env:DB_SERVICE='XEPDB1'
$env:DB_USERNAME='INVENTORY_APP'
$dbSecret=Read-Host 'Database password' -AsSecureString
$env:DB_PASSWORD=[System.Net.NetworkCredential]::new('', $dbSecret).Password
$env:SESSION_COOKIE_SECURE='false'
# For the first run only:
$env:SPRING_PROFILES_ACTIVE='dev'
$env:DEV_ADMIN_USERNAME=Read-Host 'Admin username'
$adminSecret=Read-Host 'Admin password (12+ chars)' -AsSecureString
$env:DEV_ADMIN_PASSWORD=[System.Net.NetworkCredential]::new('', $adminSecret).Password
mvn.cmd -f backend/pom.xml spring-boot:run
```

Stop with Ctrl+C. Remove secret variables afterwards: `Remove-Item Env:DB_PASSWORD,Env:DEV_ADMIN_PASSWORD`. On later runs leave DEV_ADMIN_PASSWORD unset. In terminal B, run `npm.cmd ci` and `npm.cmd run dev` inside frontend. Visit the printed URL (127.0.0.1:5173). Use this same host consistently rather than alternating localhost and 127.0.0.1 during a session.

## Build artifacts

With Java/Maven available: `mvn.cmd -f backend/pom.xml clean package` builds `backend/target/inventorypro-1.0.0.jar`. With database environment set, `java -jar backend/target/inventorypro-1.0.0.jar` starts it. `npm.cmd run build` creates `frontend/dist`. The JAR and dist are separate outputs; for local development use Vite's proxy. Production hosting would need a same-origin reverse proxy for `/api` and HTTPS. Nothing is published automatically.

## Common failures

| Symptom | Fix |
| --- | --- |
| `java`/`mvn` not recognized | Reopen terminal; verify JAVA_HOME/PATH, or use the project PowerShell helpers. Oracle's bundled Java 8 is not Java 21. |
| `npm.ps1 cannot be loaded` | Use `npm.cmd`, as documented. No permanent execution-policy change is necessary. |
| Oracle ORA-12514 | DB_SERVICE is not registered with the listener; inspect `lsnrctl status` and PDB open mode. |
| ORA-12541/17002 | Listener/service is stopped, hostname/port is wrong, or firewall blocks it. Check Windows Oracle services. |
| ORA-01017 | Wrong app credentials or connecting to root rather than the PDB where the app user exists. |
| ORA-65096 | CREATE USER ran in CDB$ROOT. Switch to XEPDB1 first; do not create a C## app user. |
| ORA-00942 | Schema not initialized for this username/service. Run 01_schema.sql once as the dedicated user. |
| ORA-01950 | App user lacks quota on its tablespace; administrator applies the quota in 00_create_user.sql. |
| ORA-00955 | Object already exists. Do not rerun the schema blindly or drop existing data. Inspect USER_TABLES. |
| ORA-00972 during CREATE USER | This XE password/identifier path may enforce 30 characters. Use 12–30 ASCII characters without quotes or ampersands. |
| Login returns connection error | Verify backend /api/health, Oracle login, and USERS table; initialize the admin with the dev helper. |
| Writes return 403 | Refresh/relogin after session expiry; ensure the frontend uses the Vite proxy and a fresh CSRF token. |
| Login seems to succeed but session lost | For loopback HTTP set SESSION_COOKIE_SECURE=false. Keep true for HTTPS environments. |
| Duplicate SKU/name or dependency conflict | Choose a unique value, use existing category/supplier IDs, or reassign dependent products first. Products with movement history remain protected. |
| Port already in use | Close the prior project server. Vite uses strict port 5173 and API defaults to 8080. |
| Dashboard zeros | Normal for a new schema; add real products and record stock movements. |

If the database is temporarily unavailable, the UI shows a connection error and retry action. It never substitutes generated product data or success responses.
