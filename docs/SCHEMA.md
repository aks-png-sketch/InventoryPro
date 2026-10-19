# Schema and invariants

All application tables live in the dedicated user schema inside XEPDB1. IDs use `NUMBER(19)` with Oracle identity columns. Quantities use `NUMBER(9,0)` and prices use `NUMBER(14,2)`. Timestamps use `TIMESTAMP DEFAULT SYSTIMESTAMP`. Service JSON maps snake_case columns to camelCase keys.

| Table | Constraints and purpose |
| --- | --- |
| USERS | Unique non-null username, non-null BCrypt hash, role constrained to ADMIN or STAFF. No seed password. |
| CATEGORIES | Unique non-null category name; referenced by products. |
| SUPPLIERS | Unique non-null supplier name, optional validated contact fields; products may have no supplier. |
| PRODUCTS | Unique SKU, required name and category, optional supplier FK, nonnegative price/quantity/reorder level. |
| STOCK_MOVEMENTS | Required product/user FKs, positive quantity, IN/OUT check, nonnegative balance_after, required reason. Immutable audit records through the API. |

Foreign keys use the default restrictive deletion behavior, never cascade. Referenced suppliers/categories and products with stock history cannot be deleted. Indexes cover category/supplier/product/user foreign keys and movement creation date. Unique constraints create their corresponding indexes.

The role column intentionally replaces a separate ROLES table: only two roles are required and the CHECK constraint prevents other values. Products are normalized against category/supplier IDs, stock movements reference users/products, and the audit stores the historical resulting balance. That audit balance is intentional denormalization for traceability.

The invariant for stock changes is: lock product row, calculate new quantity, reject negative/overflow, update product quantity, insert movement with the resulting balance, commit together. `@Transactional` applies at public service methods called from controllers. An exception from either SQL statement rolls back both. Opening stock on product creation also records an IN movement. Later product editing must pass the unchanged quantity; concurrent stock changes produce a conflict requiring refresh.

Current inventory value is `NVL(SUM(quantity*unit_price),0)`. Low-stock means `quantity>0 AND quantity<=reorder_level`; out-of-stock means `quantity=0`. These dashboard cards do not overlap. Category distribution counts products, including optional zero-count categories. Stock charts aggregate actual movements over the last 14 database calendar days; days without movements do not fabricate transactions. Movement date ranges include the entire end date using an exclusive next-day boundary. Date/time values are the local Oracle timestamp/calendar, without timezone conversion.

DDL changes in Oracle commit implicitly. Run schema creation and disposable demonstration objects separately from application transactions. Scripts 04/05 only create/drop uniquely named lab demo tables; they never truncate or drop app tables. If interrupted, inspect a leftover demo table before removing it; do not change the scripts to drop arbitrary tables.
