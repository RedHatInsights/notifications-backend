package com.redhat.cloud.notifications;

import io.vertx.core.json.JsonObject;

public class XRhIdentityUtils {

    /**
     * Generates a base64 encoded identity header contents, which just contains
     * the specified organization id.
     * @param orgId the organization id to place in the contents.
     * @return a base64 encoded string.
     */
    public static String generateEncodedXRhIdentity(final String orgId) {
        if (orgId == null || orgId.isBlank()) {
            throw new IllegalStateException("cannot generate an identity header's content from an empty organization id");
        }

        final JsonObject identity = new JsonObject();
        identity.put("org_id", orgId);

        final JsonObject json = new JsonObject();
        json.put("identity", identity);

        return Base64Utils.encode(json.toString());
    }
}
