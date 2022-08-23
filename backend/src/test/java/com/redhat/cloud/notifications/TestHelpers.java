package com.redhat.cloud.notifications;

import io.restassured.http.Header;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import static com.redhat.cloud.notifications.Constants.X_RH_IDENTITY_HEADER;

public class TestHelpers {

    public static String encodeRHIdentityInfo(String accountId, String orgId, String username) {
        JsonObject identity = new JsonObject();
        JsonObject user = new JsonObject();
        user.put("username", username);
        identity.put("account_number", accountId);
        identity.put("org_id", orgId);
        identity.put("user", user);
        identity.put("type", "User");
        JsonObject header = new JsonObject();
        header.put("identity", identity);

        return Base64Utils.encode(header.encode());
    }

    public static String encodeTurnpikeIdentityInfo(String username, String... groups) {
        JsonObject identity = new JsonObject();
        JsonObject associate = new JsonObject();
        JsonArray roles = new JsonArray();

        identity.put("auth_type", "saml-auth");
        identity.put("type", "Associate");
        identity.put("associate", associate);
        associate.put("email", username);
        associate.put("Role", roles);
        for (String group: groups) {
            roles.add(group);
        }

        JsonObject header = new JsonObject();
        header.put("identity", identity);

        return Base64Utils.encode(header.encode());
    }

    public static Header createTurnpikeIdentityHeader(String username, String... roles) {
        return new Header(X_RH_IDENTITY_HEADER, encodeTurnpikeIdentityInfo(username, roles));
    }

    public static Header createRHIdentityHeader(String encodedIdentityHeader) {
        return new Header(X_RH_IDENTITY_HEADER, encodedIdentityHeader);
    }

}
