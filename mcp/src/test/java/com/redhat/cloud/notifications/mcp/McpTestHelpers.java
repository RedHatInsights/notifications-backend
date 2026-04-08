package com.redhat.cloud.notifications.mcp;

import io.vertx.core.json.JsonObject;

import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;

public class McpTestHelpers {

    public static String encodeRHIdentityInfo(String accountId, String orgId, String username) {
        return buildIdentity(accountId, orgId, username, username);
    }

    public static String encodeRHIdentityInfoWithoutOrgId(String accountId, String username) {
        return buildIdentity(accountId, null, username, username);
    }

    public static String encodeRHIdentityInfoWithoutUserId(String accountId, String orgId, String username) {
        return buildIdentity(accountId, orgId, username, null);
    }

    public static String encodeRHIdentityInfoWithoutUsername(String accountId, String orgId, String userId) {
        return buildIdentity(accountId, orgId, null, userId);
    }

    public static String encodeRHIdentityInfoWithCustomUserId(String accountId, String orgId, String username, String userId) {
        return buildIdentity(accountId, orgId, username, userId);
    }

    public static String encodeRHIdentityInfoWithCustomUsername(String accountId, String orgId, String userId, String username) {
        return buildIdentity(accountId, orgId, username, userId);
    }

    public static String encodeRHServiceAccountIdentityInfo(String orgId, String clientId, String username) {
        JsonObject serviceAccount = new JsonObject();
        serviceAccount.put("client_id", clientId);
        serviceAccount.put("username", username);

        JsonObject identity = new JsonObject();
        identity.put("org_id", orgId);
        identity.put("service_account", serviceAccount);
        identity.put("type", "ServiceAccount");

        JsonObject header = new JsonObject();
        header.put("identity", identity);

        return new String(Base64.getEncoder().encode(header.encode().getBytes(UTF_8)), UTF_8);
    }

    private static String buildIdentity(String accountId, String orgId, String username, String userId) {
        JsonObject user = new JsonObject();
        if (username != null) {
            user.put("username", username);
        }
        if (userId != null) {
            user.put("user_id", userId);
        }

        JsonObject identity = new JsonObject();
        identity.put("account_number", accountId);
        if (orgId != null) {
            identity.put("org_id", orgId);
        }
        identity.put("user", user);
        identity.put("type", "User");

        JsonObject header = new JsonObject();
        header.put("identity", identity);

        return new String(Base64.getEncoder().encode(header.encode().getBytes(UTF_8)), UTF_8);
    }
}
