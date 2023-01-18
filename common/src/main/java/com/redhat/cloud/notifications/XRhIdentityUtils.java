package com.redhat.cloud.notifications;

import io.vertx.core.json.JsonObject;

public class XRhIdentityUtils {

    /**
     * Generates a base64 encoded identity header contents, which just contains
     * the specified account id and organization id.
     * @param accountId the EBS account number to place in the contents.
     * @param orgId the organization id to place in the contents.
     * @return a base64 encoded string.
     */
    public static String generateEncodedXRhIdentity(final String accountId, final String orgId) {
        final JsonObject identity = new JsonObject();

        if (accountId != null && !accountId.isBlank()) {
            identity.put("account_number", accountId);
        }

        if (orgId != null && !orgId.isBlank()) {
            identity.put("org_id", orgId);
        }

        final JsonObject json = new JsonObject();
        json.put("identity", identity);

        return Base64Utils.encode(json.toString());
    }
}
