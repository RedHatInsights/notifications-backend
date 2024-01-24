package com.redhat.cloud.notifications.models;

import java.util.Objects;

public class BasicAuthenticationLegacy {
    private String username;
    private String password;

    public BasicAuthenticationLegacy() { }

    public BasicAuthenticationLegacy(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof BasicAuthenticationLegacy) {
            BasicAuthenticationLegacy other = (BasicAuthenticationLegacy) o;
            return Objects.equals(username, other.username) &&
                    Objects.equals(password, other.password);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, password);
    }
}
