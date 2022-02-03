package com.redhat.cloud.notifications.models;

import javax.validation.constraints.NotNull;
import java.util.Objects;

public class BasicAuthentication {
    @NotNull
    private String username;
    @NotNull
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
