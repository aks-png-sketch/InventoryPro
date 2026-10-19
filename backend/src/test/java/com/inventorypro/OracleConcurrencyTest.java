package com.inventorypro;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static com.inventorypro.Models.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="ORACLE_TESTS",matches="true")
class OracleConcurrencyTest {
    @Test void concurrentDispatchAndAuditFailureAreAtomic() throws Exception {
        var ds=new DriverManagerDataSource("jdbc:oracle:thin:@//"+System.getenv().getOrDefault("DB_HOST","localhost")+":"+System.getenv().getOrDefault("DB_PORT","1521")+"/"+System.getenv("DB_SERVICE"),System.getenv("DB_USERNAME"),System.getenv("DB_PASSWORD"));
        var jdbc=new JdbcTemplate(ds);var s=new InventoryService(jdbc);var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        String tag="CONC-"+UUID.randomUUID();
        jdbc.update("INSERT INTO users(username,password_hash,role) VALUES(?,'not-a-login-hash','ADMIN')",tag);
        long cat=((Number)s.saveCategory(null,new CategoryInput(tag,"Concurrency test")).get("id")).longValue();
        long id=((Number)tx.execute(status->s.createProduct(new ProductInput(tag,tag,"",cat,null,new BigDecimal("2.25"),10L,2L),tag)).get("id")).longValue();
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
            Callable<Boolean> dispatch=()->{ready.countDown();start.await();try{tx.execute(status->s.move(new MovementInput(id,7L,"OUT","Concurrent dispatch"),tag));return true;}catch(org.springframework.web.server.ResponseStatusException e){assertEquals(409,e.getStatusCode().value());return false;}};
            Future<Boolean> first=pool.submit(dispatch),second=pool.submit(dispatch);
            assertTrue(ready.await(10,TimeUnit.SECONDS));start.countDown();
            assertNotEquals(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));
            assertEquals(3,jdbc.queryForObject("SELECT quantity FROM products WHERE id=?",Integer.class,id));
            assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM stock_movements WHERE product_id=?",Integer.class,id));
            // Force a NOT NULL failure in the audit insert AFTER quantity update.
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->tx.execute(status->s.move(new MovementInput(id,1L,"OUT","Forced audit failure"),"missing-"+tag)));
            assertEquals(3,jdbc.queryForObject("SELECT quantity FROM products WHERE id=?",Integer.class,id));
            assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM stock_movements WHERE product_id=?",Integer.class,id));
        } finally {
            pool.shutdownNow();
            tx.executeWithoutResult(status->{jdbc.update("DELETE FROM stock_movements WHERE product_id=?",id);jdbc.update("DELETE FROM products WHERE id=?",id);jdbc.update("DELETE FROM categories WHERE id=?",cat);jdbc.update("DELETE FROM users WHERE username=?",tag);});
        }
    }
}
