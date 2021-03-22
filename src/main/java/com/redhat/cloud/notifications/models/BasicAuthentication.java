package com.redhat.cloud.notifications.models;

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
}
