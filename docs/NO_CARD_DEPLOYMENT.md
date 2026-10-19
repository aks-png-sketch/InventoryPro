# Deployment without a payment card

Status: 19 October 2026. The Oracle Cloud signup is blocked at card verification. No cloud database or public InventoryPro service has been created.

## What is feasible

| Option | HTTPS when laptop is off | Persistent Oracle JDBC database | Card | Result |
| --- | --- | --- | --- | --- |
| OCI Always Free Autonomous Database + free Render web service | Yes, subject to idle starts and free quotas | Yes | OCI generally requires one at signup | Blocked for this account |
| Free Render web service + Oracle Database Free container on free Render | Yes | No: free Render has an ephemeral filesystem and no persistent disk; memory is also too small for a separate Oracle server | No | Do not use for records |
| Free Oracle APEX hosted workspace | Yes | An Oracle schema for APEX, but no supported external Oracle Thin JDBC endpoint for this Spring Boot application | No | A separate APEX rewrite, not this deployment |
| Render free web service + college-provided remotely hosted Oracle schema | Yes, with free-tier cold starts | Yes, if the college provides a reachable Oracle listener and permits secure remote JDBC | No, if supplied by college | Best no-card path if available |
| Existing laptop Oracle XE + a tunnel | Only while the laptop, database, app and tunnel are running | Yes, on that laptop | Often no | Temporary supervised demonstration only |

Oracle's official [Free Tier documentation](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier.htm) says most account signups require a card. Oracle [Database Free](https://www.oracle.com/database/free/get-started/) is a free self-hosted database distribution, not a hosted service. Render [free instances](https://render.com/docs/free) cannot attach persistent disks and lose local files on restart or idle spin-down; its free Postgres expires after 30 days and would replace Oracle. Oracle's [free APEX workspace](https://www.oracle.com/apex/getting-started/) is for evaluation and learning and is a different app/runtime, so its existence does not give this backend a remote JDBC database.

No verified, durable, no-card hosted Oracle JDBC service was found. A college lab server or professor-provided Oracle instance is the simplest route that meets all requirements without a payment card. Ask for a **fresh, isolated schema** and a TLS-capable connection endpoint. It must be reachable from Render's documented outbound addresses and allow schema DDL, identity columns, and a quota. Keep administrator credentials with the college; the application runs only as its dedicated schema user. Do not expose the laptop's Oracle listener to the public internet.

If the college cannot supply this, keep the local Oracle JDBC version as the DBMS lab submission and demonstrate it live from the laptop. A separately hosted static preview could show the interface, but login and inventory features would not work while the laptop is off. Do not describe that preview as a deployed full application.

## Prepared application path once remote Oracle is available

The root Dockerfile builds React and Spring Boot into one HTTPS-facing Render web service. The backend still uses `ojdbc11`, `JdbcTemplate`, Oracle SQL and transactions. Set `SPRING_PROFILES_ACTIVE=prod`, `DB_URL` to the provider's full Oracle Thin **TLS** descriptor, `DB_USERNAME=INVENTORY_APP`, `DB_PASSWORD` in Render's secret environment UI, `FORWARD_HEADERS_STRATEGY=framework`, and `SESSION_COOKIE_SECURE=true`. Use one instance and the same-origin `/api` route. The public health route is `/api/health` and queries Oracle; do not consider a mere successful container start a database check.

`scripts/Apply-CloudSchema.ps1` is a guarded, one-time helper for a **new remote TLS** `INVENTORY_APP` schema only. It reads both passwords in a local terminal, refuses if any application table already exists, applies `01_schema.sql` and `02_seed.sql`, and creates a BCrypt admin account. Oracle DDL commits independently; if setup fails after a table is created, inspect the new schema before any retry. The helper has **not** been run against any database. Do not use `00_create_user.sql` on a managed database; it targets local XE. Never run integration tests against an existing application schema.

The Render free web instance can spin down after 15 minutes idle and takes about a minute to wake; its 750 monthly instance hours are shared by services in the workspace. A free service can still be suspended for usage limits. Review [Render free limits](https://render.com/docs/free) and [billing behavior](https://render.com/docs/faq) before creating it, especially if a payment method is attached. No Render service is created by these instructions.
