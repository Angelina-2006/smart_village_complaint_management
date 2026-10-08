package org.smartvillage.db;

import org.smartvillage.model.User;
import org.smartvillage.security.Passwords;
import java.sql.*;
import java.util.Locale;

public final class UserRepository {
    public User authenticate(String email, String password) throws SQLException {
        try (Connection c = Database.connect(); PreparedStatement s = c.prepareStatement("SELECT * FROM users WHERE email=?")) {
            s.setString(1, email.toLowerCase(Locale.ROOT));
            try (ResultSet r = s.executeQuery()) {
                if (r.next() && Passwords.verify(password, r.getString("password_hash")))
                    return new User(r.getLong("id"), r.getString("name"), r.getString("email"), r.getString("role"));
            }
        }
        return null;
    }
    public void create(String name, String email, String password, String role) throws SQLException {
        try (Connection c = Database.connect(); PreparedStatement s = c.prepareStatement("INSERT INTO users(name,email,password_hash,role) VALUES(?,?,?,?)")) {
            s.setString(1, name); s.setString(2, email.toLowerCase(Locale.ROOT)); s.setString(3, Passwords.hash(password)); s.setString(4, role); s.executeUpdate();
        }
    }
    public void bootstrapAdmin() throws SQLException {
        String email = Database.setting("ADMIN_EMAIL", "").toLowerCase(Locale.ROOT);
        String password = Database.setting("ADMIN_PASSWORD", "");
        if (email.isEmpty() || password.length() < 12) throw new IllegalStateException("Set ADMIN_EMAIL and ADMIN_PASSWORD (12+ characters)");
        try (Connection c = Database.connect(); PreparedStatement s = c.prepareStatement("SELECT role FROM users WHERE email=?")) {
            s.setString(1, email);
            try (ResultSet r = s.executeQuery()) {
                if (r.next()) {
                    if (!"ADMIN".equals(r.getString(1))) throw new IllegalStateException("Admin email belongs to a villager account");
                    return;
                }
            }
        }
        create("Panchayat Administrator", email, password, "ADMIN");
    }
}
