package com.redhat.cloud.notifications.models;

import java.util.Objects;

public class BasicAuthentication {
    private String username;
    private String password;

    public BasicAuthentication() { }

    public BasicAuthentication(String username, String password) {
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
        if (o instanceof BasicAuthentication) {
            BasicAuthentication other = (BasicAuthentication) o;
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
