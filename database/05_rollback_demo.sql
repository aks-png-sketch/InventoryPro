-- Safe rollback demonstration. No application data is changed.
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
CREATE TABLE inventorypro_rollback_demo (id NUMBER PRIMARY KEY,quantity NUMBER CHECK(quantity>=0));
INSERT INTO inventorypro_rollback_demo VALUES(1,10);
COMMIT;
SAVEPOINT before_change;
UPDATE inventorypro_rollback_demo SET quantity=4 WHERE id=1;
SELECT * FROM inventorypro_rollback_demo;
ROLLBACK TO before_change;
SELECT * FROM inventorypro_rollback_demo;
INSERT INTO inventorypro_rollback_demo VALUES(2,20);
ROLLBACK;
SELECT * FROM inventorypro_rollback_demo;
DROP TABLE inventorypro_rollback_demo PURGE;
