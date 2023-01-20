package com.redhat.cloud.notifications;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class XRhIdentityUtilsTest {

    /**
     * Tests that the function under test correctly encodes the provided
     * organization ID into a base64 encoded "x-rh-identity" contents, and that
     * the decoded contents contain the given organization ID.
     */
    @Test
    void generateEncodedXRhIdentity() {
        final String orgId = "generate-encoded-x-rh-identity-organization-id";

        final String got = XRhIdentityUtils.generateEncodedXRhIdentity(orgId);

        final String expectedValue = "eyJpZGVudGl0eSI6eyJvcmdfaWQiOiJnZW5lcmF0ZS1lbmNvZGVkLXgtcmgtaWRlbnRpdHktb3JnYW5pemF0aW9uLWlkIn19";
        Assertions.assertEquals(expectedValue, got, "the x-rh-identity contents were not correctly base64 encoded");

        final String decodedContents = Base64Utils.decode(got);
        final JsonObject decodedContentsJson = new JsonObject(decodedContents);

        final JsonObject identity = decodedContentsJson.getJsonObject("identity");

        Assertions.assertNotNull(identity, "the \"identity\" key was not found in the encoded x-rh-identity encoded content");

        Assertions.assertEquals(orgId, identity.getString("org_id"), "the encoded \"org_id\" doesn't match the provided organization ID");
    }

    /**
     * Tests that when a null or a blank organization is provided, an exception
     * is thrown.
     */
    @Test
    void generateEncodedXRhIdentityBlankOrgId() {
        final String[] blankOrgIds = {null, "", "     "};

        for (final var orgId : blankOrgIds) {

            final IllegalStateException exception = Assertions.assertThrows(IllegalStateException.class, () ->
                XRhIdentityUtils.generateEncodedXRhIdentity(orgId)
            );

            final String expectedErrorMessage = "cannot generate an identity header's content from an empty organization id";

            Assertions.assertEquals(expectedErrorMessage, exception.getMessage(), "the function under test did throw an exception with the expected error message");
        }
    }

}
