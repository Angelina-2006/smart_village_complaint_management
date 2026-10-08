package org.smartvillage.model;

import java.io.Serializable;

/** Immutable encapsulated identity; no password is stored in the session. */
public final class User implements Serializable {
    private final long id;
    private final String name, email, role;
    public User(long id, String name, String email, String role) {
        this.id = id; this.name = name; this.email = email; this.role = role;
    }
    public long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
    public boolean isAdmin() { return "ADMIN".equals(role); }
}
