package com.redhat.cloud.notifications.models.secrets;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public final class SecretToken extends Secret {

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String token;

    public SecretToken() { }

    public SecretToken(final String token) {
        this.present = true;
        this.token = token;
    }

    public boolean isBlank() {
        return this.token == null || this.token.isBlank();
    }

    public String getToken() {
        return this.token;
    }

    public void setToken(final String token) {
        this.present = true;
        this.token = token;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (o instanceof SecretToken that) {
            return this.token.equals(that.token);
        }

        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.token);
    }
}
