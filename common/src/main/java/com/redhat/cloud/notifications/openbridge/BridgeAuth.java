package com.redhat.cloud.notifications.openbridge;

/**
 *
 */
public class BridgeAuth {
    private final String token;

    public BridgeAuth(String tmp) {
        token = tmp;
    }

    /**
     * Returns the token already prepended with "Bearer ",
     * so it can be used in subsequent rest-client calls as
     * header value
     * @return Token ready to use.
     */
    public String getToken() {
        return "Bearer " + token;
    }

    /**
     * Return the raw token value, that can e.g. be used
     * in conjunction with authentication in the FleetManagers OpenAPI
     * @return raw token.
     */
    public String getTokenPlain() {
        return token;
    }

    @Override
    public String toString() {
        String sb = "BridgeAuth{" + "token='" +
                token.substring(0, 10) +
                "..." + token.substring(token.length() - 10) +
                '\'' +
                '}';
        return sb;
    }
}
