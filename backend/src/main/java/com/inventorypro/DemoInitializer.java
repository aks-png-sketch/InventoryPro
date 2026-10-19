package com.inventorypro;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("demo")
public class DemoInitializer implements CommandLineRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final Environment env;
    public DemoInitializer(JdbcTemplate jdbc, PasswordEncoder encoder, Environment env) {
        this.jdbc=jdbc; this.encoder=encoder; this.env=env;
    }
    @Override public void run(String... args) {
        String username=env.getProperty("DEMO_ADMIN_USERNAME", "demo-admin");
        String password=env.getProperty("DEMO_ADMIN_PASSWORD");
        if (!username.matches("[A-Za-z0-9_.-]{3,50}") || password==null || password.length()<12 || password.getBytes(StandardCharsets.UTF_8).length>72)
            throw new IllegalArgumentException("Valid DEMO_ADMIN_USERNAME and 12-72 byte DEMO_ADMIN_PASSWORD are required");
        jdbc.update("INSERT INTO users(username,password_hash,role) VALUES(?,?,'ADMIN')", username, encoder.encode(password));
        jdbc.update("INSERT INTO categories(name,description) VALUES('Electronics','Devices and accessories')");
        jdbc.update("INSERT INTO categories(name,description) VALUES('Office supplies','Everyday workplace essentials')");
        jdbc.update("INSERT INTO categories(name,description) VALUES('Furniture','Workspace furniture')");
        jdbc.update("INSERT INTO suppliers(name,contact_person,email) VALUES('Campus Supply Co','Demo Contact','demo@example.invalid')");
        jdbc.update("INSERT INTO products(sku,name,description,category_id,supplier_id,unit_price,quantity,reorder_level) VALUES('DEMO-KEYBOARD','Wireless Keyboard','Sample product',1,1,1299.00,18,5)");
        jdbc.update("INSERT INTO products(sku,name,description,category_id,supplier_id,unit_price,quantity,reorder_level) VALUES('DEMO-MOUSE','Optical Mouse','Sample low-stock product',1,1,499.00,3,5)");
        jdbc.update("INSERT INTO products(sku,name,description,category_id,supplier_id,unit_price,quantity,reorder_level) VALUES('DEMO-NOTEBOOK','Notebook','Sample out-of-stock product',2,1,89.00,0,10)");
        jdbc.update("INSERT INTO stock_movements(product_id,user_id,movement_type,quantity,reason,balance_after) VALUES(1,1,'IN',18,'Demo opening balance',18)");
        jdbc.update("INSERT INTO stock_movements(product_id,user_id,movement_type,quantity,reason,balance_after) VALUES(2,1,'IN',3,'Demo opening balance',3)");
    }
}
