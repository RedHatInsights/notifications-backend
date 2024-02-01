package com.redhat.cloud.notifications.models.secrets;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public final class BasicAuthentication extends Secret {

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String username;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    public BasicAuthentication() { }

    public BasicAuthentication(final String username, final String password) {
        this.present = true;

        this.username = username;
        this.password = password;
    }

    public boolean isBlank() {
        return this.username == null || this.username.isBlank()
            && this.password == null || this.password.isBlank();
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(final String username) {
        this.present = true;
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(final String password) {
        this.present = true;
        this.password = password;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (o instanceof BasicAuthentication that) {
            return Objects.equals(this.username, that.username)
                && Objects.equals(this.password, that.password);
        }

        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.username, this.password);
    }
}
