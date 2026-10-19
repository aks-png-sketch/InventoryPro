package com.inventorypro;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class DevInitializer implements CommandLineRunner {
    private final JdbcTemplate jdbc; private final PasswordEncoder encoder; private final Environment env;
    public DevInitializer(JdbcTemplate jdbc,PasswordEncoder encoder,Environment env) { this.jdbc=jdbc; this.encoder=encoder; this.env=env; }
    @Override public void run(String... args) {
        String password=env.getProperty("DEV_ADMIN_PASSWORD");
        if(password==null || password.isBlank()) return;
        if(password.length()<12 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)
            throw new IllegalArgumentException("DEV_ADMIN_PASSWORD must be at least 12 characters and at most 72 UTF-8 bytes");
        String username=env.getProperty("DEV_ADMIN_USERNAME","admin");
        if(!username.matches("[A-Za-z0-9_.-]{3,50}")) throw new IllegalArgumentException("DEV_ADMIN_USERNAME must use 3-50 letters, digits, dots, underscores or hyphens");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username=?",Integer.class,username)==0)
            jdbc.update("INSERT INTO users(username,password_hash,role) VALUES(?,?,'ADMIN')",username,encoder.encode(password));
    }
}
