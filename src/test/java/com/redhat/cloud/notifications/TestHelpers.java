package com.redhat.cloud.notifications;

import io.restassured.http.Header;
import io.vertx.core.json.JsonObject;

import java.io.UnsupportedEncodingException;
import java.util.Base64;

import static org.junit.Assert.fail;

public class TestHelpers {
    public static String encodeIdentityInfo(String tenant, String username) {
        JsonObject identity = new JsonObject();
        JsonObject user = new JsonObject();
        user.put("username", username);
        identity.put("account_number", tenant);
        identity.put("user", user);
        JsonObject header = new JsonObject();
        header.put("identity", identity);

        String xRhEncoded = null;
        try {
            xRhEncoded = new String(Base64.getEncoder().encode(header.encode().getBytes("UTF-8")));
        } catch (UnsupportedEncodingException e) {
            fail();
        }
        return xRhEncoded;
    }

    public static Header createIdentityHeader(String tenant, String username) {
        return new Header("x-rh-identity", encodeIdentityInfo(tenant, username));
    }

    public static Header createIdentityHeader(String encodedIdentityHeader) {
        return new Header("x-rh-identity", encodedIdentityHeader);
    }
}
