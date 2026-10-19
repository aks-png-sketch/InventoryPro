# Testing

Current audit results and boundaries are recorded in [QA_AUDIT.md](QA_AUDIT.md). Build `frontend/dist` before running the isolated harness: its production Maven profile bundles and tests the frontend index in the JAR. The harness now runs the expanded `qa_http.py` scenario and restarts only the isolated server for persistence verification.

## Automated layers

Frontend `npm.cmd test`: stock-status boundaries, decimal input preservation/optional relationships, local date filtering and monetary formatting. `npm.cmd run build`: complete React/Tailwind/Recharts compilation.

Backend `scripts/Test-Backend.ps1`: Spring MVC security/authorization/CSRF, protected identity/logout, validated product create/update/delete, staff movement permission, pagination, stock service boundaries, row-lock SQL usage, insufficient stock, overflow, quantity-edit prevention, relationship error propagation, CSV escaping and reversed dates. MVC tests use mocked services and are not represented as Oracle tests.

`OracleIntegrationTest` and `OracleConcurrencyTest` are opt-in real-database tests, enabled only with `ORACLE_TESTS=true`. They connect using DB_HOST/PORT/SERVICE/USERNAME/PASSWORD, not an embedded database. Use a disposable dedicated schema with `01_schema.sql`. One test creates unique fixture records inside a rollback transaction and verifies CRUD, join relationships, stock balances, aggregate valuation and FK rejection. Another commits uniquely named fixtures, runs two simultaneous dispatches, verifies exactly one succeeds, forces an audit insert failure after the quantity update, verifies the quantity rolls back, then explicitly removes only its own fixtures.

To run against your own disposable schema, set environment variables with a locally prompted password as in JDBC.md, then:

```powershell
$env:ORACLE_TESTS='true'
powershell -ExecutionPolicy Bypass -File .\scripts\Test-Backend.ps1
Remove-Item Env:ORACLE_TESTS,Env:DB_PASSWORD
```

Test quantities, sequence increments and temporary fixture commits make these inappropriate for a shared/production schema. The tests do not silently provision a database or delete existing tables. Verify the recorded results in [VERIFICATION.md](VERIFICATION.md); skipped Oracle tests do not prove connectivity.

For this local XE installation, `scripts/Verify-OracleLab.ps1` is an optional administrator-assisted harness that reproduces schema scripts, Oracle tests, the direct JDBC demo and packaged-server HTTP checks. It needs the portable tools under `.tools/` and local SQL*Plus OS administrator authentication. It creates a GUID-named temporary schema in XEPDB1 with generated process-only credentials, uses port 8081, stops its server, and drops only the schema it successfully created. Run it explicitly from the root with `powershell -ExecutionPolicy Bypass -File .\scripts\Verify-OracleLab.ps1`. This harness does not provision or change INVENTORY_APP and is not needed for normal application startup.

## Manual end-to-end checklist

- [ ] Run schema as INVENTORY_APP in XEPDB1; verify USER and CON_NAME.
- [ ] Initialize an admin locally; ensure no password appears in repository files.
- [ ] Login with valid/invalid passwords; refresh and verify persistence; logout.
- [ ] Create category and supplier with all contacts; edit and view them.
- [ ] Reject blank names, invalid email, negative price/quantity and duplicate SKU.
- [ ] Create product with zero stock; edit metadata; view category and supplier names.
- [ ] Receive 8 and dispatch 3; confirm 5 units and two audit entries.
- [ ] Reject dispatch 6 without changing balance/history.
- [ ] Concurrently dispatch 4 twice from balance 5; only one succeeds, final quantity 1.
- [ ] Create product with opening quantity; verify the opening balance IN audit.
- [ ] Reject deletion of referenced category/supplier and product with movements.
- [ ] Delete a zero-stock/no-history temporary product successfully.
- [ ] Search name/SKU, combine filters, sort price/name/quantity both ways, paginate.
- [ ] Verify dashboard counts and SUM(quantity*unit_price) against SQL*Plus.
- [ ] Check recent product activity, recent stock movements, category mix and chart.
- [ ] Open all seven reports; test inclusive end-date range for movements.
- [ ] Export CSV with comma/quote/formula-like names; confirm quoted/escaped values.
- [ ] Create Staff and verify UI restrictions plus backend 403 for admin writes.
- [ ] Logout or expire session; protected APIs return 401 and UI returns to login.
- [ ] Test light/dark, keyboard tab navigation, Escape/modal focus and mobile width.
- [ ] Stop backend; observe clear error without fake data; restart and retry.
- [ ] Stop/restart backend with same Oracle schema; verify persistence.
- [ ] Run direct JdbcDemo and safe SQL 03/04/05 in SQL*Plus.

Automated API checks do not replace a visual browser review. No screenshots should be claimed unless actually captured.
