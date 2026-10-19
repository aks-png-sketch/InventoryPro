# InventoryPro

A complete local inventory workspace for a Database Systems Lab: React, Vite, Tailwind CSS, Recharts, Java 21, Spring Boot, Spring Security and **Oracle Database through JDBC**. There is no replacement database and no fabricated dashboard data.

The interface includes a responsive sidebar, light/dark appearance, login, product catalogue, search/filter/sort/pagination, category and supplier directories, atomic stock-in/out operations, movement history, live dashboard, seven reports, CSV exports, admin team-account creation, validation, confirmation dialogs and connection errors.

## Repository

For a fresh clone, install the software in [Windows setup](docs/WINDOWS_SETUP.md), provision a dedicated user in your own Oracle PDB with the [SQL scripts](database/), then follow the startup steps below. The `XEPDB1` defaults describe a local Oracle XE example; use your own PDB service if it differs. The [JDBC guide](docs/JDBC.md) shows the Spring integration and a standalone `DriverManager` demonstration for lab inspection.

```text
frontend/   React UI, API client, Tailwind styling, frontend tests
backend/    Spring Boot REST API, security, JdbcTemplate, direct JDBC demo, tests
database/   Dedicated-user setup, schema, seeds, SQL and transaction demos
docs/       Architecture, schema, API, JDBC, Windows setup, lab walkthrough, tests
scripts/    Windows startup and verification helpers
.env.example  Configuration reference; never put actual passwords here
```

## Start locally on Windows

**Already set up?** In the PowerShell window running the backend, press `Ctrl+C` and wait for it to stop. In a new PowerShell window, run:

```powershell
cd C:\path\to\InventoryPro
powershell -ExecutionPolicy Bypass -File .\scripts\Start-Backend.ps1
```

Enter only the `INVENTORY_APP` database password at the local prompt. Do not use `-InitializeAdmin` again; your existing admin login remains in Oracle. Wait for `Started InventoryProApplication` and `HikariPool-1 - Start completed`, then check `http://localhost:8080/api/health` and sign in at `http://127.0.0.1:5173/`. Keep that PowerShell window open while using the app.

Example configuration: Oracle 21c XE, `localhost:1521`, service `XEPDB1`. The application defaults to `INVENTORY_APP` inside XEPDB1; all connection fields remain configurable through environment variables. SYSTEM is only for schema provisioning.

1. Open SQL*Plus as SYSTEM using your locally entered password. In that SQL*Plus window:

   ```sql
   ALTER SESSION SET CONTAINER = XEPDB1;
   SHOW CON_NAME;
   @database/00_create_user.sql
   CONNECT INVENTORY_APP@//localhost:1521/XEPDB1
   SHOW USER;
   SHOW CON_NAME;
   @database/01_schema.sql
   @database/02_seed.sql
   ```

   The setup script asks for a database password without echoing it. CONNECT asks for the same password. Choose a strong password between 12 and 30 ASCII characters for compatibility with this XE installation, without `"` or `&` because SQL*Plus substitution is used. The schema script is **one-time only** in a fresh schema. If INVENTORY_APP already exists, do not recreate or drop it; inspect its tables first. The seed script is repeatable and only adds three categories, with no fake products or stock activity.

2. Open **PowerShell terminal A** at the project root:

   ```powershell
   cd C:\path\to\InventoryPro
   powershell -ExecutionPolicy Bypass -File .\scripts\Start-Backend.ps1 -InitializeAdmin
   ```

   Enter the application database password locally, choose an admin username, and enter an admin login password of at least 12 characters (at most 72 UTF-8 bytes). These are separate passwords. Initialization runs only in the development profile, inserts an account only if the username is absent, and never resets existing credentials. After first initialization, use the same command **without `-InitializeAdmin`**. The backend listens on port 8080. Wait for `Started InventoryProApplication`.

3. Open **PowerShell terminal B**:

   ```powershell
   cd frontend
   npm.cmd ci
   npm.cmd run dev
   ```

4. Open **http://127.0.0.1:5173/** and sign in using the admin username/password you just created. Create suppliers and products, record stock movements, and open Reports. The frontend proxies `/api` to localhost:8080 so session cookies and CSRF work on the same browser origin.

Install Java 21, Maven 3.9 and Node.js as described in [Windows setup](docs/WINDOWS_SETUP.md). The PowerShell helpers also detect optional local tools under ignored `.tools/`; that directory is not included in Git.

## Configuration and JDBC

The Thin URL is `jdbc:oracle:thin:@//${DB_HOST}:${DB_PORT}/${DB_SERVICE}`. Defaults are host `localhost`, port `1521`, service `XEPDB1` (local XE example), username `INVENTORY_APP`. `DB_PASSWORD` is required and has no default. Override any variable in the terminal that starts Java:

```powershell
$env:DB_HOST = 'localhost'
$env:DB_PORT = '1521'
$env:DB_SERVICE = 'XEPDB1'
$env:DB_USERNAME = 'INVENTORY_APP'
```

The startup helper asks for secrets securely and passes them only in the child process environment. `.env.example` is documentation, **not automatically loaded** by Spring or PowerShell. No production password is stored in the repository. `SESSION_COOKIE_SECURE` defaults to true; the local startup helper sets false only for loopback HTTP. Use HTTPS and leave it true in a production configuration. No deployment is part of this project setup.

Spring uses Oracle's official `com.oracle.database.jdbc:ojdbc11:23.26.3.0.0` dependency, `JdbcTemplate` and Spring transactions. No JPA/Hibernate. See [JDBC guide](docs/JDBC.md) for the direct `DriverManager` demonstration and resource handling.

## Verification

```powershell
# Project root
powershell -ExecutionPolicy Bypass -File .\scripts\Test-Backend.ps1
# Frontend directory
npm.cmd test
npm.cmd run build
```

Oracle integration tests require `ORACLE_TESTS=true` and DB environment variables against a dedicated test schema initialized with `01_schema.sql`. These tests use real Oracle, not H2. See [testing and manual checklist](docs/TESTING.md). Do not enable them on a shared production schema.

## Database and security decisions

- Oracle PKs, FKs, unique keys, checks, indexes, identity IDs and `NUMBER` money/quantities; Java uses `BigDecimal` for money.
- Product quantities change through stock movements after creation. `SELECT ... FOR UPDATE` serializes requests on a product, then quantity update and audit insert commit or roll back together.
- Products with historical movements cannot be deleted. Categories/suppliers referenced by products cannot be deleted. Relationships are preserved by database constraints.
- Admins manage products, suppliers, categories and user creation. Staff can read inventory/reports and record stock-in/out. The API enforces roles independently of the UI.
- BCrypt hashes, session fixation protection, session invalidation on logout, HttpOnly/SameSite cookies, CSRF on every write including login/logout. No wildcard CORS: the Vite proxy provides same-origin development.
- Request validation, prepared parameters, allowlisted sort fields, no raw SQL in errors, CSV formula escaping.
- Inventory reports reflect the **current snapshot**. The stock movement report alone uses date ranges. INR is the presentation currency; monetary calculations stay decimal in Oracle/Java.

## Lab documentation

- [Windows installation and troubleshooting](docs/WINDOWS_SETUP.md)
- [Architecture and ER diagram](docs/ARCHITECTURE.md)
- [Database schema and constraints](docs/SCHEMA.md)
- [API reference and examples](docs/API.md)
- [JDBC integration](docs/JDBC.md)
- [DDL/DML presentation guide](docs/LAB_DEMO.md)
- [Automated and manual testing](docs/TESTING.md)
- [Recorded verification results and limitations](docs/VERIFICATION.md)
- [Comprehensive QA audit and current test totals](docs/QA_AUDIT.md)
- [Deployment architecture, environment variables and release gates](docs/DEPLOYMENT.md)
- [GitHub publishing review and commands](docs/GITHUB.md)

This is a local university project. No GitHub push or deployment has been performed. Screenshots are included only if actually captured; none have been fabricated.
