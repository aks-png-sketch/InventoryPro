-- SELECT, INNER JOIN, LEFT JOIN and ORDER BY
SELECT p.sku,p.name,c.name category,s.name supplier,p.quantity,p.unit_price
FROM products p INNER JOIN categories c ON c.id=p.category_id
LEFT JOIN suppliers s ON s.id=p.supplier_id ORDER BY p.name;
-- GROUP BY, HAVING and aggregate functions
SELECT c.name,COUNT(p.id) product_count,SUM(p.quantity*p.unit_price) value
FROM categories c LEFT JOIN products p ON p.category_id=c.id
GROUP BY c.name HAVING COUNT(p.id)>0 ORDER BY value DESC;
-- Subquery: products priced above the catalogue average
SELECT sku,name,unit_price FROM products WHERE unit_price>(SELECT AVG(unit_price) FROM products);
-- Low stock includes zero here; UI low-stock and out-of-stock reports are separate.
SELECT sku,name,quantity,reorder_level FROM products WHERE quantity<=reorder_level;
SELECT NVL(SUM(quantity*unit_price),0) inventory_value,COUNT(*) products FROM products;
SELECT m.created_at,p.name,u.username,m.movement_type,m.quantity,m.balance_after
FROM stock_movements m JOIN products p ON m.product_id=p.id JOIN users u ON m.user_id=u.id
ORDER BY m.created_at DESC;
