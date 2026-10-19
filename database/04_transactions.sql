-- Safe DDL and DML demo using a dedicated disposable table only.
-- Run separately from application transactions: Oracle DDL commits implicitly.
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
CREATE TABLE inventorypro_lab_demo (id NUMBER PRIMARY KEY,label VARCHAR2(50),quantity NUMBER CHECK(quantity>=0));
ALTER TABLE inventorypro_lab_demo ADD note VARCHAR2(100);
INSERT INTO inventorypro_lab_demo(id,label,quantity) VALUES(1,'Lab demo',10);
COMMIT;
SELECT quantity FROM inventorypro_lab_demo WHERE id=1 FOR UPDATE;
UPDATE inventorypro_lab_demo SET quantity=quantity-3 WHERE id=1 AND quantity>=3;
COMMIT;
SELECT * FROM inventorypro_lab_demo;
DELETE FROM inventorypro_lab_demo WHERE id=1;
COMMIT;
DROP TABLE inventorypro_lab_demo PURGE;
-- Application equivalent: SELECT product FOR UPDATE, validate, UPDATE quantity,
-- INSERT stock_movements, COMMIT; any exception rolls BOTH changes back.
