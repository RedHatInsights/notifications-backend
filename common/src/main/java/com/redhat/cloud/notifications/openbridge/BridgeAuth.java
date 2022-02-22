package com.redhat.cloud.notifications.openbridge;

/**
 *
 */
public class BridgeAuth {
    private String token;

    public BridgeAuth(String tmp) {
        token = tmp;
    }

    public String getToken() {
        return token;
    }
}
