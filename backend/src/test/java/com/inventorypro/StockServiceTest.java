package com.inventorypro;

import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.Map;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.inventorypro.Models.*;

class StockServiceTest {
    @Test void datesOutsideOracleRangeRejected() {assertThrows(ResponseStatusException.class,()->service.checkDates(java.time.LocalDate.of(2026,1,1),java.time.LocalDate.of(9999,12,31)));}
    @Test void csvMultilineFormulaIsNeutralized() {assertTrue(ApiController.csvCell("=SUM(1,2)\nmore").startsWith("\"'="));}
    JdbcTemplate jdbc; InventoryService service;
    @BeforeEach void setup() { jdbc=mock(JdbcTemplate.class);service=spy(new InventoryService(jdbc)); }
    void balance(long n) { doReturn(Map.of("quantity",n)).when(service).one("SELECT quantity FROM products WHERE id=? FOR UPDATE",1L);doReturn(Map.of("id",1L,"quantity",n)).when(service).product(1L); }
    @Test void stockInLocksRowAndRecordsAudit() {
        balance(10);service.move(new MovementInput(1L,5L,"IN","Delivery"),"staff");
        verify(jdbc).update("UPDATE products SET quantity=?,updated_at=SYSTIMESTAMP WHERE id=?",15L,1L);
        verify(jdbc).update("INSERT INTO stock_movements(product_id,user_id,movement_type,quantity,reason,balance_after) VALUES(?,(SELECT id FROM users WHERE username=?),?,?,?,?)",1L,"staff","IN",5L,"Delivery",15L);
    }
    @Test void stockOutCanReachZero() { balance(10);service.move(new MovementInput(1L,10L,"OUT","Dispatch"),"staff");verify(jdbc).update("UPDATE products SET quantity=?,updated_at=SYSTIMESTAMP WHERE id=?",0L,1L); }
    @Test void insufficientStockWritesNothing() { balance(2);assertThrows(ResponseStatusException.class,()->service.move(new MovementInput(1L,3L,"OUT","Dispatch"),"staff"));verifyNoInteractions(jdbc); }
    @Test void overflowWritesNothing() { balance(999999999);assertThrows(ResponseStatusException.class,()->service.move(new MovementInput(1L,1L,"IN","Delivery"),"staff"));verifyNoInteractions(jdbc); }
    @Test void productEditCannotBypassStockAudit() {
        balance(5);var p=new ProductInput("SKU","Product","",1L,null,new BigDecimal("9.99"),10L,3L);
        assertThrows(ResponseStatusException.class,()->service.updateProduct(1L,p));verifyNoInteractions(jdbc);
    }
    @Test void referencedSupplierDeletionPropagatesConstraint() {doThrow(new DataIntegrityViolationException("FK")).when(jdbc).update("DELETE FROM suppliers WHERE id=?",1L);assertThrows(DataIntegrityViolationException.class,()->service.deleteSupplier(1L));}
    @Test void reversedDateRangeRejected() {assertThrows(ResponseStatusException.class,()->service.checkDates(java.time.LocalDate.of(2026,10,20),java.time.LocalDate.of(2026,10,19)));}
    @Test void sortInjectionRejectedBeforeDatabaseUse() {assertThrows(ResponseStatusException.class,()->service.products("",null,"all","name; DROP TABLE products","asc",1,20));verifyNoInteractions(jdbc);}
    @Test void csvPreventsSpreadsheetFormulaExecutionAndEscapesQuotes() {assertEquals("\"'=HYPERLINK(\"\"evil\"\")\"",ApiController.csvCell("=HYPERLINK(\"evil\")"));assertEquals("\"a,b\"",ApiController.csvCell("a,b"));}
}
