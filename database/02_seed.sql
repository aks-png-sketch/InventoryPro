-- Optional, repeatable reference data. No users, passwords or fabricated analytics.
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
MERGE INTO categories c USING (SELECT 'Electronics' name,'Devices and accessories' description FROM dual) s
ON(c.name=s.name) WHEN NOT MATCHED THEN INSERT(name,description) VALUES(s.name,s.description);
MERGE INTO categories c USING (SELECT 'Office supplies' name,'Everyday workplace essentials' description FROM dual) s
ON(c.name=s.name) WHEN NOT MATCHED THEN INSERT(name,description) VALUES(s.name,s.description);
MERGE INTO categories c USING (SELECT 'Furniture' name,'Workspace furniture' description FROM dual) s
ON(c.name=s.name) WHEN NOT MATCHED THEN INSERT(name,description) VALUES(s.name,s.description);
COMMIT;
-- Create real suppliers/products through the app. Dashboard starts honestly at zero.
-- Create the development admin using the documented dev profile initialization.
