package com.redhat.cloud.notifications.recipients;

import java.util.Objects;

public class User {

    private String username;
    private String email;
    private Boolean isActive;
    private Boolean isAdmin;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Boolean isActive() {
        return isActive;
    }

    public void setActive(Boolean active) {
        isActive = active;
    }

    public Boolean isAdmin() {
        return isAdmin;
    }

    public void setAdmin(Boolean admin) {
        isAdmin = admin;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User)) return false;
        User user = (User) o;
        return username.equals(user.username) && email.equals(user.email) && isActive.equals(user.isActive) && isAdmin.equals(user.isAdmin);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, email, isActive, isAdmin);
    }
}
