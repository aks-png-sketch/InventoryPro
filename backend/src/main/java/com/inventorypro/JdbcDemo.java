package com.inventorypro;

import java.sql.*;
import java.util.UUID;

/** Standalone direct JDBC demonstration; no Spring context. */
public class JdbcDemo {
    public static void main(String[] args) throws SQLException {
        String url="jdbc:oracle:thin:@//"+env("DB_HOST","localhost")+":"+env("DB_PORT","1521")+"/"+required("DB_SERVICE");
        try(Connection c=DriverManager.getConnection(url,required("DB_USERNAME"),required("DB_PASSWORD"))) {
            c.setAutoCommit(false);
            String name="JDBC-DEMO-"+UUID.randomUUID();
            try {
                try(PreparedStatement p=c.prepareStatement("INSERT INTO categories(name,description) VALUES(?,?)")) { p.setString(1,name);p.setString(2,"Temporary JDBC demonstration");System.out.println("Inserted: "+p.executeUpdate()); }
                try(PreparedStatement p=c.prepareStatement("SELECT id,name FROM categories WHERE name=?")) { p.setString(1,name);try(ResultSet rs=p.executeQuery()) { while(rs.next()) System.out.println(rs.getLong("id")+" "+rs.getString("name")); } }
                try(PreparedStatement p=c.prepareStatement("UPDATE categories SET description=? WHERE name=?")) { p.setString(1,"Updated by PreparedStatement");p.setString(2,name);p.executeUpdate(); }
                try(PreparedStatement p=c.prepareStatement("DELETE FROM categories WHERE name=?")) { p.setString(1,name);p.executeUpdate(); }
                c.commit(); System.out.println("INSERT/SELECT/UPDATE/DELETE committed; no demo records retained.");
                try(PreparedStatement p=c.prepareStatement("INSERT INTO categories(name) VALUES(?)")) { p.setString(1,name+"-ROLLBACK");p.executeUpdate(); }
                c.rollback(); System.out.println("Second insert rolled back.");
            } catch(SQLException e) { c.rollback(); throw e; }
        }
    }
    private static String required(String key) { String v=System.getenv(key);if(v==null||v.isBlank()) throw new IllegalArgumentException("Set "+key);return v; }
    private static String env(String key,String fallback) { String v=System.getenv(key);return v==null ? fallback : v; }
}
