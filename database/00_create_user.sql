-- Run as a database administrator INSIDE the chosen pluggable database.
-- First: SHOW PDBS; then ALTER SESSION SET CONTAINER = your_pdb;
-- SQL*Plus prompts without echoing the password. Never save credentials here.
SET VERIFY OFF
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
ACCEPT app_password CHAR PROMPT 'New inventory_app password: ' HIDE
CREATE USER inventory_app IDENTIFIED BY "&app_password";
GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE TO inventory_app;
ALTER USER inventory_app QUOTA 100M ON USERS;
UNDEFINE app_password
-- Reconnect as inventory_app using the PDB service, not SYS/SYSTEM.
