# REST API

Base path `/api`. JSON on success except logout/delete (204) and CSV. Errors use `{ "message": "...", "fields": { "field": "validation message" } }` where applicable. Lists of products/movements return `{items,total,page,size}`; category/supplier/report endpoints return arrays. IDs and counts are JSON numbers. Money is represented by Java BigDecimal/Oracle NUMBER; frontend formatting uses INR.

All endpoints require a session except CSRF, health and login. GET `/auth/csrf` first, retain its session cookie, and submit its `headerName`/`token` on every write. Login form uses `application/x-www-form-urlencoded` username/password, not JSON. Fetch a new CSRF token after successful login because authentication rotates it. Cookies must accompany subsequent requests.

| Method | Path | Role | Description |
| --- | --- | --- | --- |
| GET | /health | Public | Real `SELECT 1 FROM dual`; returns UP or 503. |
| GET | /auth/csrf | Public | CSRF token and header name. |
| POST | /auth/login | Public + CSRF | Form login, returns 200, 401 invalid credentials or 503 auth storage failure. |
| GET | /auth/me | Any | username and role. |
| POST | /auth/logout | Any + CSRF | Invalidate session, 204. |
| POST | /users | Admin | Create ADMIN/STAFF with BCrypt hash, 201. |
| GET | /products | Any | Search/filter/sort/paginate. |
| GET | /products/{id} | Any | Product with category/supplier names. |
| POST | /products | Admin | Create product and opening movement if quantity > 0, 201. |
| PUT | /products/{id} | Admin | Update metadata; unchanged quantity required. |
| DELETE | /products/{id} | Admin | Delete only without history, 204. |
| GET/POST | /categories | Any/Admin | List or create (201). |
| PUT/DELETE | /categories/{id} | Admin | Update or delete (204), preserving FKs. |
| GET/POST | /suppliers | Any/Admin | List or create (201). |
| PUT/DELETE | /suppliers/{id} | Admin | Update or delete (204), preserving FKs. |
| GET | /movements | Any | Required from/to dates, optional page/size. |
| POST | /movements | Any | Atomic stock adjustment, 201 with updated product. |
| GET | /dashboard | Any | Real metrics, categories, chart, recent movements and product activity. |
| GET | /reports/{type} | Any | Required from/to dates; JSON records. |
| GET | /reports/{type}/csv | Any | Same report as downloadable UTF-8 CSV. |

Products query example: `/products?search=cable&category=1&status=low&sort=price&direction=desc&page=1&size=20`. `status`: all, healthy, low, out. `sort`: name, price, quantity. `direction`: asc/desc. Page starts at 1 (max 1,000,000), size 1–100. Defaults: page 1, size 20, name ascending. Search matches literal substrings in name/SKU and has a 150-character limit.

Product create/update body:

```json
{"sku":"CBL-001","name":"USB cable","description":"1 metre","categoryId":1,"supplierId":null,"unitPrice":199.50,"quantity":0,"reorderLevel":5}
```

Supplier body: `{"name":"Supply Co","contactPerson":"Contact","email":"contact@example.com","phone":"0123456789","address":"Office address"}`. Category body: `{"name":"Accessories","description":"Product accessories"}`.

Movement body:

```json
{"productId":1,"quantity":10,"type":"IN","reason":"Receipt PO-001"}
```

Use OUT for dispatches. Quantity must be a positive integer. The response includes the resulting product quantity. Do not blindly retry an ambiguous network failure after a stock submission: first inspect movement history, since a completed transaction may already have committed. The system does not implement idempotency keys.

User body: `{"username":"staff.one","password":"<enter-locally>","role":"STAFF"}`. Password is 12+ characters, at most 72 UTF-8 bytes, stored only as BCrypt. Username allows 3–50 ASCII letters, digits, dot, underscore and hyphen. There is no public registration or password-reset flow.

Movement queries: `?from=2026-10-01&to=2026-10-31&page=1&size=20`. Report types: `valuation`, `low-stock`, `out-of-stock`, `movements`, `categories`, `suppliers`, `summary`. All require valid ISO from/to; only movements applies the date range, other reports are current snapshots. CSV exports all matching rows and can use more memory on very large datasets; intended for this lab's data volumes.

Status codes: 400 invalid input, 401 no valid session, 403 role/CSRF denied, 404 missing record, 409 unique/FK conflict/stock conflict, 503 unavailable Oracle. Schema constraint messages intentionally avoid exposing SQL. The UI displays the returned error and leaves forms open for correction.
