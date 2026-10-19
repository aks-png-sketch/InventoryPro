# Oracle JDBC connectivity

The Maven dependency uses the official Thin driver, so installing a separate JAR in the source tree is unnecessary. Spring Boot builds a HikariCP DataSource using the environment-configured URL, username and password. JDBC accesses the PDB/schema specified, with no privileged account at runtime.

`InventoryService.rows` calls `JdbcTemplate.query`, reads `ResultSet` metadata and values, and creates camelCase maps for JSON. Writes use `JdbcTemplate.update` and placeholders; creation requests generated identity keys via a prepared statement and `GeneratedKeyHolder`. JdbcTemplate closes statements and result sets and returns pooled connections. Spring binds one connection to the transaction during `@Transactional` methods, committing on success and rolling back unchecked SQL/service exceptions.

`move` explicitly executes `SELECT quantity FROM products WHERE id=? FOR UPDATE`. A second operation on that product waits for the first transaction, then reads its committed quantity. It cannot spend the same units twice. Other products can be processed independently. A CHECK constraint additionally prevents negative stored quantities.

## Standalone professor demonstration

`backend/src/main/java/com/inventorypro/JdbcDemo.java` intentionally uses no Spring context. It demonstrates:

- DriverManager.getConnection, Connection.setAutoCommit(false)
- PreparedStatement parameters, executeQuery, ResultSet iteration
- INSERT/UPDATE/DELETE with executeUpdate
- commit and rollback
- try-with-resources for Connection, PreparedStatement and ResultSet

It creates a uniquely named temporary category, selects/updates/deletes it and commits the net-zero change; then rolls back another insert. Existing categories/products are untouched.

With Java/Maven on PATH, in project-root PowerShell:

```powershell
$env:DB_HOST='localhost'
$env:DB_PORT='1521'
$env:DB_SERVICE='XEPDB1'
$env:DB_USERNAME='INVENTORY_APP'
$secretValue=Read-Host 'Application DB password' -AsSecureString
$env:DB_PASSWORD=[System.Net.NetworkCredential]::new('', $secretValue).Password
mvn.cmd -f backend/pom.xml compile exec:exec '-Dexec.executable=java' '-Dexec.args=-classpath %classpath com.inventorypro.JdbcDemo'
Remove-Item Env:DB_PASSWORD
```

For this workspace's portable tools, first set:

```powershell
$env:JAVA_HOME=(Get-ChildItem .tools/java -Directory | Select-Object -First 1).FullName
$env:Path="$env:JAVA_HOME\bin;$PWD\.tools\maven\apache-maven-3.9.11\bin;$env:Path"
```

These PATH additions affect only the current terminal. Never pass a password as a command-line argument or save it in a `.sql`, `.properties` or tracked `.env` file.
