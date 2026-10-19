# InventoryPro QA audit — 19 October 2026

**Verdict: the final automated suite passes; public deployment is blocked pending browser, container and cloud-environment validation.** This is functional QA and a scoped security review, not a penetration test or a production reliability certification.

## Final executed results

| Suite | Passed | Failed/errors | Skipped |
| --- | ---: | ---: | ---: |
| Spring MVC security/API tests | 17 | 0 | 0 |
| Stock service/validation/CSV tests | 11 | 0 | 0 |
| Real Oracle integration/constraint tests | 2 | 0 | 0 |
| Real Oracle concurrency/atomic rollback test | 1 | 0 | 0 |
| Frontend Node tests | 11 | 0 | 0 |
| **Automated test cases** | **42** | **0** | **0** |
| Additional packaged-server HTTP assertions | 149 | 0 | — |
| Additional restart/persistence HTTP assertions | 6 | 0 | — |

The 155 HTTP assertions are separately reported assertions inside a Python scenario, not 155 independent unit tests. Early expanded runs exposed a fractional-quantity bug and a test-harness plain-text response assumption. Both were corrected; the final run completed without failures. Maven package and frontend production build passed. `npm audit` returned **0 known vulnerabilities** for the installed frontend dependency graph; this is not a comprehensive Java dependency or application security scan.

Evidence: `backend/target/surefire-reports/TEST-*.xml`, ignored `.tools/qa-oracle.log`, `.tools/qa-http.json`, `.tools/qa-http-restart.json`, `.tools/npm-audit.json`, `.tools/qa-secrets.json`. These generated artifacts remain local and are excluded from Git. Reproduce the database/HTTP run with `scripts/Verify-OracleLab.ps1` after building frontend/dist; it uses a disposable test schema, not INVENTORY_APP.

## Data protection and actual application connectivity

- Existing port-8080 application was left running. `/api/health` and its Vite proxy both returned 200 with Oracle UP.
- Read-only Oracle inspection found 10 `INVENTORY_APP` sessions from `InventoryProApplication`, plus the user's SQL*Plus connection.
- Read-only inspection found one application user and one BCrypt-formatted hash; the stored hash and password were not printed.
- Existing application product count was 1 at inspection. No test mutations were sent to port 8080 or the existing schema.
- All SELECT/INSERT/UPDATE/DELETE, failure injection, concurrency and restart writes used GUID-named `IP_TEST_...` schemas in XEPDB1, with generated credentials held in the harness process. Cleanup removed only the schema that the harness successfully created.
- Restart/persistence was tested on the isolated backend at port 8081. The user's current backend was **not restarted**. Its current classes remain those loaded before QA; restart it manually to load backend fixes.

## Backend, JDBC and functional coverage

| Feature | Current-run evidence | Status/limit |
| --- | --- | --- |
| SELECT/INSERT/UPDATE/DELETE | Real Oracle CRUD integration, standalone DriverManager demo, HTTP CRUD | Passed |
| COMMIT and ROLLBACK | Direct JDBC demo, SQL 04/05, transaction integration | Passed |
| Constraints | Direct Oracle NOT NULL, UNIQUE, FK, role/price/quantity CHECK violations | Passed |
| Concurrent stock updates | Two simultaneous dispatches, row lock, exactly one success | Passed |
| Audit atomicity | Forced audit-insert failure after quantity update, verified full rollback | Passed |
| Persistence after process restart | Committed product metadata and stock retrieved after isolated server restart | Passed |
| Login and invalid credentials | Real BCrypt login, invalid/injection-like username, authenticated identity | Passed via HTTP |
| Logout/session handling | Cookies HttpOnly/SameSite=Lax, logout invalidation, unauthenticated requests | Passed via HTTP; real 30-minute expiry and HTTPS cookies need browser/staging checks |
| Admin/Staff | Staff reads/reports/moves stock; admin product/category/supplier/user writes denied to staff | Passed via HTTP/MVC |
| Dashboard | Zero baseline; exact product/value/low-stock/out-of-stock metrics; IN/OUT and category aggregates | Passed for JSON calculations; chart rendering not browser-verified |
| Products | Create/read/update/delete, history-protected deletion, joins, timestamps, SKU/decimal/quantity validation | Passed via Oracle/HTTP |
| Search/filter/sort | SKU search, category and stock status, name/price/quantity directions, escaped percent wildcard, SQL-like inputs | Passed via HTTP; visual pagination not browser-verified |
| Categories/suppliers | Create/update/read/delete and dependent-record rejection; email validation | Passed via HTTP |
| Stock-in/out | Exact balances, zero/invalid type rejection, insufficient stock with no audit/quantity change | Passed |
| Low/out-of-stock | Reorder threshold and zero-stock classifications, distinct filters | Passed for API; alert/badge appearance not browser-verified |
| Valuation | Decimal Oracle/Java calculation and API value checked against fixture quantities/prices | Passed |
| Reports/date filters | All seven reports, movement range exclusion, reversed-date rejection | Passed |
| CSV | Download/content headers, parsed row counts, quoting/formula regression tests | Passed at HTTP/helper level; browser download UX needs manual check |
| Production frontend serving | Combined production JAR serves index without authentication; assets remain public by security policy | Passed for index HTTP and MVC public-asset rule; actual browser execution not tested |

## Bugs fixed during this audit

1. **Fractional quantity silently truncated:** Jackson accepted JSON `1.5` for a Long and turned it into `1`. Disabled float-to-integer coercion; HTTP and MVC regression tests now require 400. This also protects other integer IDs/counts from truncation.
2. **Multiline CSV formula prefix bypass:** the regex did not match formula-like fields containing line breaks. Changed to direct leading-character inspection and added a regression test.
3. **Stale CSRF after denied/expired requests:** API client now discards cached tokens after 401/403 and clears them on session-expiry handling. No stock mutation is automatically retried.
4. **Stale modal Escape closure:** modal keyboard handler now calls the current close callback so saving-state guards are respected. Source/build checked; interactive regression still needs a browser.
5. **Invalid API response treated as success:** an HTML response from a misrouted proxy could become null successful data. It now produces a connection/configuration error, with a frontend regression test.
6. **CSV navigation bypassed API error handling:** export now uses the authenticated API client and displays errors within Reports, with an exporting state. API text-response regression test passes; browser download behavior remains manual.
7. **Extreme report dates could fail past Oracle's supported timestamp range:** added explicit supported year boundaries before the next-day end-date calculation, with a regression test.

Deployment improvements: configurable frontend API base also covers exports; optional complete DB_URL for remote/TLS Oracle; hosting PORT and trusted-proxy configuration; credentialed CORS only for exact configured origins; a combined production Maven profile; public static assets with protected APIs; non-root multistage Dockerfile and secret/build exclusions.

## Security review

- BCrypt code, real authenticated login and actual stored hash format verified. No plaintext credential was requested, printed, or added to source.
- Protected endpoints, role restrictions, CSRF-required writes, logout invalidation and rejected untrusted CORS origins exercised. Explicit allowlisted CORS preflight exercised by MVC tests. Default same-origin remains recommended.
- Parameterized SQL and sort/direction allowlists reviewed and attacked with scoped invalid/injection-like requests in the test schema. This is not exhaustive SQL injection fuzzing.
- CSV formula injection regression checked, including multiline fields.
- Production Secure cookies default true; local HTTP harness explicitly sets false. HTTPS proxy/cookie behavior is a staging/browser release gate.
- High-confidence source scan found no private-key blocks, common GitHub/OpenAI/AWS key patterns in 51 upload-eligible files at scan time. This is a pattern scan, not proof against every secret format. Configuration/SQL files use environment placeholders/local prompts; remaining added documentation contains no credentials.
- A local checkout outside the OneDrive synchronization directory was verified before publication preparation. The original OneDrive `.git` contained cloud reparse points that interrupted Git. The source and metadata were copied without deleting the original refs or objects; the local checkout passed `git status`. The publication copy still requires staged-diff review before any upload.
- Login throttling, password reset/account lifecycle, broad Java CVE scanning, load/soak testing, HTTPS dynamic scanning and backup recovery are not covered by passing functional tests. Configure upstream abuse protection before public exposure.

## Frontend/browser coverage and manual checklist

The in-app browser failed to initialize with `failed to start Node runtime: The system cannot find the path specified. (os error 3)`. The Product Design screenshot audit could not proceed because it requires actual captures. No screenshots, console-clean claim, responsive approval or accessibility certification is fabricated. The independent code/API audit continued as authorized.

1. **Login/navigation — manual pending:** sign in, reload, switch every sidebar page, sign out and verify return to login. Inspect browser console/network errors.
2. **Forms — manual pending:** create/edit isolated supplier/category/product records, check required/email/number validation, cancel/save controls, confirmation dialogs, tab order and focus restoration. Do not modify personal existing records for this check.
3. **Stock — API passed, UI pending:** receive 8, dispatch 3, reject dispatch 6, inspect balance/history. Never retry an ambiguous stock POST until checking history; idempotency keys are not implemented.
4. **Dashboard/reports — API passed, UI pending:** inspect rendered charts/tooltips, badge states, date controls, CSV browser download and denied/expired-session export feedback.
5. **Responsive/themes — manual pending:** widths 390, 768 and 1440; sidebar open/close, table horizontal scrolling, modal bounds, light/dark readability and keyboard focus.
6. **Loading/empty/error states — manual pending:** use an isolated staging frontend/backend to throttle requests, empty lists and simulate unavailable API responses. Do not stop the user's existing database to demonstrate this.

Node tests verify API-client behavior, formatting and stock classification; they do not render React components or replace these browser checks.

## Deployment conclusion and next steps

See [DEPLOYMENT.md](DEPLOYMENT.md) for the exact environment table, architecture, cost sources, cloud schema prerequisites, Docker validation and future Git commands. Recommended lab setup: one paid same-origin HTTPS web service plus separate Oracle Autonomous Database through TLS JDBC. A free Oracle database is an affordable lab option with documented reliability/backup limits; paid database recovery is appropriate for important records.

Before deployment: finish browser QA, build/smoke-test the Docker image (Docker is absent here), obtain an approved cloud Oracle endpoint and test its TLS/network settings, provision a new cloud schema without overwriting local records, configure bootstrap/secrets/HTTPS/rate limiting/backups, then seek approval to upload/deploy. No push, deployment, cloud resource creation or migration of existing records was performed.

To load QA fixes locally, stop only your backend with Ctrl+C and rerun `scripts/Start-Backend.ps1` **without `-InitializeAdmin`**, entering the existing Oracle password locally. Existing admin and application records remain in Oracle. Then repeat the browser checklist. Database schema changes are not required by these fixes.
