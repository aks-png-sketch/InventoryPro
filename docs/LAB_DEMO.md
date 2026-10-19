# Professor demonstration walkthrough

1. Show the folder structure, POM Oracle dependency, `application.yml` URL placeholders, schema script and ER diagram. Explain why Oracle/PDB/app-user credentials are distinct from application login credentials.
2. Sign in as the development admin and show that an invalid password is rejected. Reload and confirm session persistence. Show `/auth/me` and CSRF exchange in browser network tools without exposing passwords.
3. Create a category and supplier. Create a product at ₹12.50 with zero opening quantity. Edit its name and reorder level. Search by SKU, filter by category/status, change sort order.
4. Receive 8 units with a receipt reference. Dispatch 3 units. Verify balance 5, movement history IN 8 / OUT 3 and dashboard value ₹62.50 for this product. Try dispatching 6; verify conflict and unchanged quantity/history.
5. Explain the two SQL writes in `move`, `FOR UPDATE`, `@Transactional` and the foreign keys. Show the concurrency/rollback tests if available. Never demonstrate failures by disabling application constraints.
6. Open valuation, low-stock, out-of-stock, category, supplier, summary and movement reports. Change dates on the movement report; export CSV. Explain that valuation uses current prices, not historical cost accounting.
7. Try deleting a supplier/category referenced by a product; verify understandable rejection. Try deleting a product with movement history; verify history stays protected. Create a zero-stock/no-history temporary product and demonstrate successful deletion.
8. Create a Staff account through Team, sign out, sign in as Staff. Verify read/report/stock access and missing admin actions; direct admin API requests must also fail with 403.
9. Run `JdbcDemo` against the same app schema, then explain DriverManager, Connection, PreparedStatement, ResultSet, executeQuery/executeUpdate, commit/rollback and automatic resource closure.
10. In SQL*Plus as INVENTORY_APP, run `03_queries.sql`. Identify SELECT, JOIN, GROUP BY, HAVING, aggregates, subquery and ORDER BY. Run 04/05 to demonstrate CREATE/ALTER/DROP and INSERT/UPDATE/DELETE/COMMIT/ROLLBACK on disposable tables. Oracle DDL commits implicitly, so these are deliberately separate from real app operations.
11. Toggle appearance and resize the browser, demonstrate empty states and validation. Temporarily stopping the backend produces a real connection error; restart it and retry. Do not stop the entire Oracle instance just for a demo if other work uses it.
12. Log out and verify protected APIs refuse the old session.

Safe SQL*Plus invocation from the project root:

```sql
CONNECT INVENTORY_APP@//localhost:1521/XEPDB1
@database/03_queries.sql
@database/04_transactions.sql
@database/05_rollback_demo.sql
```

The DDL scripts create their own demo objects and drop only those objects. Schema creation belongs to initial setup, not every presentation run. No user passwords, real private contacts or sensitive screenshots belong in a public GitHub repository.
