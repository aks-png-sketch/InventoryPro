package com.inventorypro;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.math.BigDecimal;
import java.util.*;
import static com.inventorypro.Models.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt in against a disposable Oracle test schema initialized with 01_schema.sql.
 * All fixtures roll back; identity sequence increments are intentionally not transactional. */
@EnabledIfEnvironmentVariable(named="ORACLE_TESTS",matches="true")
class OracleIntegrationTest {
    @Test void oracleEnforcesNotNullUniqueForeignKeyAndCheckConstraints() {
        var ds=new DriverManagerDataSource("jdbc:oracle:thin:@//"+env("DB_HOST","localhost")+":"+env("DB_PORT","1521")+"/"+env("DB_SERVICE",""),System.getenv("DB_USERNAME"),System.getenv("DB_PASSWORD"));
        var jdbc=new JdbcTemplate(ds);var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        tx.executeWithoutResult(status->{try {
            String tag="CK-"+UUID.randomUUID();
            jdbc.update("INSERT INTO categories(name) VALUES(?)",tag);
            long cat=jdbc.queryForObject("SELECT id FROM categories WHERE name=?",Long.class,tag);
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("INSERT INTO categories(name) VALUES(?)",tag));
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("INSERT INTO categories(name) VALUES(NULL)"));
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("INSERT INTO users(username,password_hash,role) VALUES(?,'test','OWNER')",tag));
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("INSERT INTO products(sku,name,category_id,quantity) VALUES(?,?,?,-1)",tag,tag,cat));
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("INSERT INTO products(sku,name,category_id,unit_price) VALUES(?,?,?,-1)",tag,tag,cat));
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("INSERT INTO products(sku,name,category_id) VALUES(?,?,999999999)",tag,tag));
        } finally {status.setRollbackOnly();}});
    }
    @Test void realOracleCrudRelationshipsStockAndValuation() {
        var ds=new DriverManagerDataSource("jdbc:oracle:thin:@//"+env("DB_HOST","localhost")+":"+env("DB_PORT","1521")+"/"+env("DB_SERVICE",""),System.getenv("DB_USERNAME"),System.getenv("DB_PASSWORD"));
        var jdbc=new JdbcTemplate(ds);var service=new InventoryService(jdbc);var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        tx.executeWithoutResult(status->{try {
            String tag="IT-"+UUID.randomUUID();
            var encoder=new BCryptPasswordEncoder();String hash=encoder.encode(tag);
            assertTrue(encoder.matches(tag,hash));
            jdbc.update("INSERT INTO users(username,password_hash,role) VALUES(?,?,'ADMIN')",tag,hash);
            long category=((Number)service.saveCategory(null,new CategoryInput(tag,"Test")).get("id")).longValue();
            long supplier=((Number)service.saveSupplier(null,new SupplierInput(tag,"Contact","test@example.invalid","","Test")).get("id")).longValue();
            BigDecimal before=new BigDecimal(service.dashboard().get("inventoryValue").toString());
            var p=new ProductInput(tag,tag,"Test",category,supplier,new BigDecimal("12.50"),0L,5L);
            long id=((Number)service.createProduct(p,tag).get("id")).longValue();
            service.updateProduct(id,new ProductInput(tag,"Updated","Test",category,supplier,new BigDecimal("12.50"),0L,5L));
            assertEquals("Updated",service.product(id).get("name"));
            final long deletedId=id;
            service.deleteProduct(id);assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.product(deletedId));
            id=((Number)service.createProduct(p,tag).get("id")).longValue();final long productId=id;
            service.move(new MovementInput(id,8L,"IN","Test receipt"),tag);
            service.move(new MovementInput(id,3L,"OUT","Test dispatch"),tag);
            assertEquals(5,((Number)service.product(id).get("quantity")).intValue());
            assertEquals(0,before.add(new BigDecimal("62.50")).compareTo(new BigDecimal(service.dashboard().get("inventoryValue").toString())));
            assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.move(new MovementInput(productId,6L,"OUT","Insufficient"),tag));
            assertEquals(5,((Number)service.product(id).get("quantity")).intValue());
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->service.deleteSupplier(supplier));
            assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM stock_movements WHERE product_id=?",Integer.class,id));
        } finally {status.setRollbackOnly();}});
    }
    static String env(String key,String fallback) {return System.getenv().getOrDefault(key,fallback);}
}
