package org.smartvillage.db;

import java.sql.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public final class Database {
    private Database() {}
    public static String setting(String key, String fallback) {
        String value = System.getProperty(key, System.getenv(key));
        return value == null || value.isBlank() ? fallback : value;
    }
    public static Connection connect() throws SQLException {
        return DriverManager.getConnection(setting("DB_URL", "jdbc:mysql://localhost:3306/smart_village?serverTimezone=UTC"), setting("DB_USER", "village"), setting("DB_PASSWORD", ""));
    }
    public static void initialize() throws SQLException, IOException {
        try (InputStream in = Database.class.getResourceAsStream("/schema.sql"); Connection c = connect(); Statement s = c.createStatement()) {
            if (in == null) throw new IOException("Missing schema.sql");
            for (String sql : new String(in.readAllBytes(), StandardCharsets.UTF_8).split(";"))
                if (!sql.isBlank()) s.execute(sql);
        }
    }
}
