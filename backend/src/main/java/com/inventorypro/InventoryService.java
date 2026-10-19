package com.inventorypro;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.sql.*;
import java.time.LocalDate;
import static com.inventorypro.Models.*;

@Service
public class InventoryService {
    private final JdbcTemplate jdbc;
    public InventoryService(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public List<Map<String,Object>> rows(String sql,Object... args) {
        return jdbc.query(sql,(rs,n) -> {
            Map<String,Object> row=new LinkedHashMap<>();
            for(int i=1;i<=rs.getMetaData().getColumnCount();i++) {
                String[] parts=rs.getMetaData().getColumnLabel(i).toLowerCase(Locale.ROOT).split("_");
                StringBuilder key=new StringBuilder(parts[0]);
                for(int k=1;k<parts.length;k++) key.append(Character.toUpperCase(parts[k].charAt(0))).append(parts[k].substring(1));
                int sqlType=rs.getMetaData().getColumnType(i);
                Object value=(sqlType==Types.TIMESTAMP || sqlType==Types.TIMESTAMP_WITH_TIMEZONE || sqlType==Types.DATE) ? rs.getTimestamp(i) : rs.getObject(i);
                if(value instanceof Timestamp t) value=t.toLocalDateTime().toString();
                row.put(key.toString(),value);
            }
            return row;
        },args);
    }
    public Map<String,Object> one(String sql,Object... args) {
        return rows(sql,args).stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Record not found"));
    }
    private static final String PRODUCTS="SELECT p.*,c.name category_name,s.name supplier_name FROM products p JOIN categories c ON c.id=p.category_id LEFT JOIN suppliers s ON s.id=p.supplier_id";
    public Map<String,Object> products(String search,Long category,String status,String sort,String direction,int page,int size) {
        StringBuilder where=new StringBuilder(" WHERE 1=1"); List<Object> args=new ArrayList<>();
        if(search!=null && !search.isBlank()) { where.append(" AND (LOWER(p.name) LIKE ? ESCAPE '\\' OR LOWER(p.sku) LIKE ? ESCAPE '\\')"); String q="%"+search.toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%"; args.add(q);args.add(q); }
        if(category!=null) { where.append(" AND p.category_id=?");args.add(category); }
        switch(status) { case "low" -> where.append(" AND p.quantity>0 AND p.quantity<=p.reorder_level"); case "out" -> where.append(" AND p.quantity=0"); case "healthy" -> where.append(" AND p.quantity>p.reorder_level"); case "all" -> {} default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid stock status"); }
        String order=switch(sort) {case "name" -> "p.name";case "price" -> "p.unit_price";case "quantity" -> "p.quantity";default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid sort field");};
        if(!Set.of("asc","desc").contains(direction)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid sort direction");
        Long total=jdbc.queryForObject("SELECT COUNT(*) FROM products p"+where,Long.class,args.toArray());
        args.add((page-1)*size);args.add(size);
        return Map.of("items",rows(PRODUCTS+where+" ORDER BY "+order+" "+direction+",p.id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",args.toArray()),"total",total,"page",page,"size",size);
    }
    public Map<String,Object> product(long id) { return one(PRODUCTS+" WHERE p.id=?",id); }
    private long nextId(String table,String sequenceColumn,String sql,Object... args) {
        org.springframework.jdbc.support.GeneratedKeyHolder key=new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(connection -> { PreparedStatement ps=connection.prepareStatement(sql,new String[]{sequenceColumn}); for(int i=0;i<args.length;i++) ps.setObject(i+1,args[i]); return ps; },key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }
    @Transactional public Map<String,Object> createProduct(ProductInput p,String username) {
        long id=nextId("products","ID","INSERT INTO products(sku,name,description,category_id,supplier_id,unit_price,quantity,reorder_level) VALUES(?,?,?,?,?,?,?,?)",p.sku(),p.name(),p.description(),p.categoryId(),p.supplierId(),p.unitPrice(),p.quantity(),p.reorderLevel());
        if(p.quantity()>0) logMovement(id,p.quantity(),"IN","Opening balance",username,p.quantity());
        return product(id);
    }
    @Transactional public Map<String,Object> updateProduct(long id,ProductInput p) {
        Map<String,Object> current=one("SELECT quantity FROM products WHERE id=? FOR UPDATE",id);
        if(((Number)current.get("quantity")).longValue()!=p.quantity()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Use Stock movements to change quantity; refresh if stock changed.");
        jdbc.update("UPDATE products SET sku=?,name=?,description=?,category_id=?,supplier_id=?,unit_price=?,reorder_level=?,updated_at=SYSTIMESTAMP WHERE id=?",p.sku(),p.name(),p.description(),p.categoryId(),p.supplierId(),p.unitPrice(),p.reorderLevel(),id);
        return product(id);
    }
    public void deleteProduct(long id) { changed(jdbc.update("DELETE FROM products WHERE id=?",id)); }
    private void changed(int count) { if(count==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Record not found"); }
    @Transactional public Map<String,Object> move(MovementInput m,String username) {
        Map<String,Object> p=one("SELECT quantity FROM products WHERE id=? FOR UPDATE",m.productId());
        long quantity=((Number)p.get("quantity")).longValue();
        long updated=m.type().equals("IN") ? quantity+m.quantity() : quantity-m.quantity();
        if(updated<0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Insufficient stock. Available: "+quantity);
        if(updated>999999999) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Quantity exceeds supported limit");
        jdbc.update("UPDATE products SET quantity=?,updated_at=SYSTIMESTAMP WHERE id=?",updated,m.productId());
        logMovement(m.productId(),m.quantity(),m.type(),m.reason(),username,updated);
        return product(m.productId());
    }
    private void logMovement(long id,long quantity,String type,String reason,String username,long balance) {
        jdbc.update("INSERT INTO stock_movements(product_id,user_id,movement_type,quantity,reason,balance_after) VALUES(?,(SELECT id FROM users WHERE username=?),?,?,?,?)",id,username,type,quantity,reason,balance);
    }
    public List<Map<String,Object>> categories() { return rows("SELECT c.*, (SELECT COUNT(*) FROM products p WHERE p.category_id=c.id) product_count FROM categories c ORDER BY c.name"); }
    public Map<String,Object> saveCategory(Long id,CategoryInput c) {
        if(id==null) id=nextId("categories","ID","INSERT INTO categories(name,description) VALUES(?,?)",c.name(),c.description());
        else changed(jdbc.update("UPDATE categories SET name=?,description=? WHERE id=?",c.name(),c.description(),id));
        return one("SELECT * FROM categories WHERE id=?",id);
    }
    public void deleteCategory(long id) { changed(jdbc.update("DELETE FROM categories WHERE id=?",id)); }
    public List<Map<String,Object>> suppliers() { return rows("SELECT s.*,(SELECT COUNT(*) FROM products p WHERE p.supplier_id=s.id) product_count FROM suppliers s ORDER BY s.name"); }
    public Map<String,Object> saveSupplier(Long id,SupplierInput s) {
        if(id==null) id=nextId("suppliers","ID","INSERT INTO suppliers(name,contact_person,email,phone,address) VALUES(?,?,?,?,?)",s.name(),s.contactPerson(),s.email(),s.phone(),s.address());
        else changed(jdbc.update("UPDATE suppliers SET name=?,contact_person=?,email=?,phone=?,address=? WHERE id=?",s.name(),s.contactPerson(),s.email(),s.phone(),s.address(),id));
        return one("SELECT * FROM suppliers WHERE id=?",id);
    }
    public void deleteSupplier(long id) { changed(jdbc.update("DELETE FROM suppliers WHERE id=?",id)); }
    private static final String MOVEMENTS="SELECT m.*,p.name product_name,p.sku,u.username FROM stock_movements m JOIN products p ON p.id=m.product_id JOIN users u ON u.id=m.user_id";
    public Map<String,Object> movements(LocalDate from,LocalDate to,int page,int size) {
        checkDates(from,to);
        String where=" WHERE m.created_at>=? AND m.created_at<?";
        Object start=Timestamp.valueOf(from.atStartOfDay()), end=Timestamp.valueOf(to.plusDays(1).atStartOfDay());
        return Map.of("items",rows(MOVEMENTS+where+" ORDER BY m.created_at DESC,m.id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",start,end,(page-1)*size,size),"total",jdbc.queryForObject("SELECT COUNT(*) FROM stock_movements m"+where,Long.class,start,end),"page",page,"size",size);
    }
    public void checkDates(LocalDate from,LocalDate to) {
        if(from.getYear()<1 || to.getYear()>9998) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Dates must be between years 0001 and 9998");
        if(from.isAfter(to)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Start date must precede end date");
    }
    public Map<String,Object> dashboard() {
        Map<String,Object> result=new LinkedHashMap<>(one("SELECT COUNT(*) total_products,NVL(SUM(unit_price*quantity),0) inventory_value,NVL(SUM(CASE WHEN quantity>0 AND quantity<=reorder_level THEN 1 ELSE 0 END),0) low_stock,NVL(SUM(CASE WHEN quantity=0 THEN 1 ELSE 0 END),0) out_of_stock FROM products"));
        result.put("totalSuppliers",jdbc.queryForObject("SELECT COUNT(*) FROM suppliers",Long.class));
        result.put("categories",rows("SELECT c.name,COUNT(p.id) product_count,NVL(SUM(p.quantity*p.unit_price),0) inventory_value FROM categories c LEFT JOIN products p ON c.id=p.category_id GROUP BY c.name ORDER BY c.name"));
        result.put("chart",rows("SELECT TO_CHAR(created_at,'YYYY-MM-DD') day,SUM(CASE WHEN movement_type='IN' THEN quantity ELSE 0 END) stock_in,SUM(CASE WHEN movement_type='OUT' THEN quantity ELSE 0 END) stock_out FROM stock_movements WHERE created_at>=TRUNC(SYSDATE)-13 GROUP BY TO_CHAR(created_at,'YYYY-MM-DD') ORDER BY day"));
        result.put("recent",rows(MOVEMENTS+" ORDER BY m.created_at DESC,m.id DESC FETCH FIRST 6 ROWS ONLY"));
        result.put("activity",rows("SELECT id,name,sku,quantity,updated_at FROM products ORDER BY updated_at DESC,id DESC FETCH FIRST 5 ROWS ONLY"));
        return result;
    }
    public List<Map<String,Object>> report(String type,LocalDate from,LocalDate to) {
        checkDates(from,to);
        return switch(type) {
            case "valuation" -> rows("SELECT p.sku,p.name,c.name category,p.quantity,p.unit_price,p.quantity*p.unit_price inventory_value FROM products p JOIN categories c ON c.id=p.category_id ORDER BY inventory_value DESC");
            case "low-stock" -> rows(PRODUCTS+" WHERE p.quantity>0 AND p.quantity<=p.reorder_level ORDER BY p.quantity");
            case "out-of-stock" -> rows(PRODUCTS+" WHERE p.quantity=0 ORDER BY p.name");
            case "movements" -> rows(MOVEMENTS+" WHERE m.created_at>=? AND m.created_at<? ORDER BY m.created_at DESC",Timestamp.valueOf(from.atStartOfDay()),Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
            case "categories" -> rows("SELECT c.name,COUNT(p.id) product_count,NVL(SUM(p.quantity),0) total_units,NVL(SUM(p.quantity*p.unit_price),0) inventory_value FROM categories c LEFT JOIN products p ON p.category_id=c.id GROUP BY c.name ORDER BY c.name");
            case "suppliers" -> rows("SELECT NVL(s.name,'Unassigned') supplier,p.sku,p.name,p.quantity,p.unit_price,p.quantity*p.unit_price inventory_value FROM products p LEFT JOIN suppliers s ON s.id=p.supplier_id ORDER BY supplier,p.name");
            case "summary" -> List.of(one("SELECT COUNT(*) total_products,NVL(SUM(quantity),0) total_units,NVL(SUM(quantity*unit_price),0) inventory_value,NVL(SUM(CASE WHEN quantity<=reorder_level THEN 1 ELSE 0 END),0) needs_reorder FROM products"));
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unknown report");
        };
    }
}
