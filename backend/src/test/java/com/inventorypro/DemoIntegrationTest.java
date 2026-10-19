package com.inventorypro;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import static com.inventorypro.Models.*;

@SpringBootTest(properties={"DEMO_ADMIN_PASSWORD=temporary-test-password-1234","SESSION_COOKIE_SECURE=false"})
@ActiveProfiles("demo")
class DemoIntegrationTest {
    @Autowired InventoryService service;
    @Test void seededDashboardAndReportsLoad() {
        assertEquals(3L, ((Number)service.dashboard().get("totalProducts")).longValue());
        assertEquals(3, service.report("valuation", java.time.LocalDate.now().minusDays(1), java.time.LocalDate.now()).size());
    }
    @Test @Transactional void productSupplierAndStockWorkflow() {
        long category=((Number)service.saveCategory(null,new CategoryInput("Test category", "Isolated test")).get("id")).longValue();
        long supplier=((Number)service.saveSupplier(null,new SupplierInput("Test supplier",null,null,null,null)).get("id")).longValue();
        ProductInput input=new ProductInput("TEST-H2", "Test item", null, category, supplier, new BigDecimal("12.50"), 0L, 2L);
        long id=((Number)service.createProduct(input,"demo-admin").get("id")).longValue();
        assertEquals(5L, ((Number)service.move(new MovementInput(id,5L,"IN","Receipt"),"demo-admin").get("quantity")).longValue());
        assertEquals(3L, ((Number)service.move(new MovementInput(id,2L,"OUT","Dispatch"),"demo-admin").get("quantity")).longValue());
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.move(new MovementInput(id,4L,"OUT","Too many"),"demo-admin"));
        assertEquals(3L, ((Number)service.product(id).get("quantity")).longValue());
        assertEquals(1L, ((Number)service.products("TEST-H2",category,"all","name","asc",1,20).get("total")).longValue());
        assertFalse(service.report("movements",java.time.LocalDate.now().minusDays(1),java.time.LocalDate.now()).isEmpty());
    }
}
