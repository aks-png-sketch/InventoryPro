# Recorded verification — 19 October 2026

The later comprehensive audit supersedes these initial setup counts. See [QA_AUDIT.md](QA_AUDIT.md) for the final 42 test cases and 155 additional HTTP assertions, current fixes and remaining release gates.

Verified on this Windows workspace, with Node 24.20.0, project-local Java 21.0.12.1, Maven 3.9.11, Spring Boot 3.5.16 and Oracle Database 21c XE. Oracle listener confirmed TCP 1521 and `xepdb1`; SQL*Plus confirmed XEPDB1 READ WRITE.

| Check | Executed result |
| --- | --- |
| Frontend dependency installation | Passed; package-lock.json generated. |
| Frontend `npm.cmd test` | 4 passed, 0 failed. |
| Frontend `npm.cmd run build` | Passed; production assets generated in ignored frontend/dist. |
| Backend Maven test/package | Passed; executable JAR generated. |
| Backend MVC/security tests | 13 passed. |
| Backend stock/validation/CSV tests | 9 passed. |
| Real Oracle CRUD/valuation integration test | 1 passed. |
| Real Oracle concurrency/atomic rollback test | 1 passed. |
| Total backend tests with Oracle enabled | **24 passed, 0 failures, 0 errors, 0 skipped.** |
| All SQL scripts 01–05 | Executed successfully against an isolated temporary XEPDB1 schema. |
| Standalone DriverManager JDBC demo | Passed SELECT/INSERT/UPDATE/DELETE, commit and rollback on real Oracle. |
| Packaged backend /api/health | UP using real Oracle Thin JDBC. |
| HTTP BCrypt login/session/CSRF | Passed, including fetching a rotated token after login. |
| HTTP category/supplier/product creation and joins | Passed. |
| HTTP stock-in/out and filtered product query | Passed, verified balance 7 after +10/−3. |
| HTTP dashboard valuation | Passed: 7 × 19.95 = 139.65. |
| HTTP all seven report types and their CSV endpoints | Passed. |
| HTTP logout/session invalidation | Passed: subsequent identity call returned 401. |
| Frontend loopback HTTP | 200 at http://127.0.0.1:5173/. |

Oracle integration used a uniquely named temporary schema and generated credentials held in the test process, never committed. Only that test schema was removed afterwards. It did not use SYSTEM as the application runtime user, and did not modify an existing INVENTORY_APP schema. JDBC integration is verified; your persistent INVENTORY_APP account still requires the locally chosen password and one-time provisioning in README.

Real-database testing caught and corrected an Oracle aggregate/scalar-subquery incompatibility and Oracle TIMESTAMP object serialization before the final passing run. Stock concurrency testing ran two simultaneous dispatches against the same row; exactly one succeeded, leaving the correct nonnegative balance. A forced movement audit insert failure rolled back the preceding quantity update.

## Remaining verification and limits

- Browser automation could not initialize: `failed to start Node runtime: The system cannot find the path specified. (os error 3)`. No visual screenshots or responsive browser checks are claimed. The manual visual/accessibility checklist remains in TESTING.md.
- Your chosen persistent database password and admin login password must be entered locally. These are not requested in chat or stored in repository configuration.
- Tests use mock MVC for role/validation checks plus actual Oracle integration and packaged-server HTTP checks. They do not claim every UI interaction has been automated.
- Session identity survives page reload, but sessions are server-memory sessions and expire on backend restart or after 30 minutes of inactivity. Records remain in Oracle.
- Current inventory valuation is not a historical accounting ledger. The chart covers days with recorded movement in the last 14 database calendar days.
- CSV reports are materialized in memory; this is appropriate for lab datasets, not very large warehouse exports. Stock POSTs do not have idempotency keys; check history before retrying an ambiguous network failure.
- No account password-change/reset, account deletion, audit-history deletion, GitHub push or deployment was implemented/requested as part of the core workflow.

Reference requirements checked: [Spring Boot Java compatibility](https://docs.spring.io/spring-boot/3.5/system-requirements.html), [Vite 7 Node requirements](https://v7.vite.dev/guide/), [official Oracle JDBC Maven artifact](https://central.sonatype.com/artifact/com.oracle.database.jdbc/ojdbc11). Actual installed frontend versions are pinned by package-lock.json; backend versions are pinned by the POM and Spring Boot dependency management.
