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

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("BridgeAuth{");
        sb.append("token='");
        sb.append(token.substring(0, 10));
        sb.append("...").append(token.substring(token.length() - 10))
                        .append('\'');
        sb.append('}');
        return sb.toString();
    }
}
