package com.inventorypro;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** One-time, guarded setup for a new cloud application schema. Not run by the web app. */
public final class CloudSchemaInitializer {
    private static final Pattern TLS = Pattern.compile("(?i)(\\(protocol=tcps\\)|@tcps://)");
    private CloudSchemaInitializer() { }

    public static void main(String[] args) throws Exception {
        String url = required("DB_URL");
        String username = required("DB_USERNAME");
        String password = required("DB_PASSWORD");
        String adminUsername = required("CLOUD_ADMIN_USERNAME");
        String adminPassword = required("CLOUD_ADMIN_PASSWORD");
        validateTarget(url, username);
        validateAdmin(adminUsername, adminPassword);
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            String actualUser;
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT SYS_CONTEXT('USERENV','SESSION_USER') FROM dual")) {
                result.next();
                actualUser = result.getString(1);
            }
            if (!username.equalsIgnoreCase(actualUser)) {
                throw new IllegalStateException("Connected as an unexpected Oracle user");
            }
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT COUNT(*) FROM user_tables WHERE table_name IN ('USERS','CATEGORIES','SUPPLIERS','PRODUCTS','STOCK_MOVEMENTS')")) {
                result.next();
                if (result.getInt(1) != 0) {
                    throw new IllegalStateException("Application tables already exist; refusing to rerun the one-time schema");
                }
            }
            connection.setAutoCommit(false);
            Path database = Files.isDirectory(Path.of("database")) ? Path.of("database") : Path.of("../database");
            execute(connection, database.resolve("01_schema.sql"));
            execute(connection, database.resolve("02_seed.sql"));
            try (var insert = connection.prepareStatement("INSERT INTO users(username,password_hash,role) VALUES(?,?,'ADMIN')")) {
                insert.setString(1, adminUsername);
                insert.setString(2, new BCryptPasswordEncoder().encode(adminPassword));
                insert.executeUpdate();
            }
            connection.commit();
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT COUNT(*) FROM user_tables WHERE table_name IN ('USERS','CATEGORIES','SUPPLIERS','PRODUCTS','STOCK_MOVEMENTS')")) {
                result.next();
                if (result.getInt(1) != 5) {
                    throw new IllegalStateException("Schema setup did not create all five application tables");
                }
            }
            System.out.println("Cloud Oracle JDBC setup verified: five application tables and one hashed admin account");
        }
    }

    static void validateTarget(String url, String username) {
        if (!url.startsWith("jdbc:oracle:thin:@") || !TLS.matcher(url).find()
                || url.toLowerCase(Locale.ROOT).contains("localhost")
                || url.contains("127.0.0.1")) {
            throw new IllegalArgumentException("DB_URL must be a remote Oracle Thin TLS URL");
        }
        if (!username.equalsIgnoreCase("INVENTORY_APP")) {
            throw new IllegalArgumentException("Cloud setup requires the dedicated INVENTORY_APP user");
        }
    }

    static void validateAdmin(String username, String password) {
        if (!username.matches("[A-Za-z0-9_.-]{3,50}")) throw new IllegalArgumentException("Invalid admin username");
        if (password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException("Admin password must be 12 or more characters and at most 72 UTF-8 bytes");
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }

    private static void execute(Connection connection, Path path) throws Exception {
        String sql = Files.readString(path, StandardCharsets.UTF_8).lines()
                .filter(line -> !line.stripLeading().toUpperCase(Locale.ROOT).startsWith("WHENEVER SQLERROR"))
                .reduce("", (left, right) -> left + right + "\n");
        ScriptUtils.executeSqlScript(connection, new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)));
    }
}
