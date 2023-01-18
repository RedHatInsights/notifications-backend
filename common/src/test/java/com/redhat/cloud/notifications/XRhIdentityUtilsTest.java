package com.redhat.cloud.notifications;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class XRhIdentityUtilsTest {

    /**
     * Tests that when generating an encoded x-rh-identity content with both
     * an account number and an org id, the content is encoded as expected.
     */
    @Test
    void generateEncodedXRhIdentity() {
        final String got = XRhIdentityUtils.generateEncodedXRhIdentity("ebs-account-number", "organization-id");

        final String expectedValue = "eyJpZGVudGl0eSI6eyJhY2NvdW50X251bWJlciI6ImVicy1hY2NvdW50LW51bWJlciIsIm9yZ19pZCI6Im9yZ2FuaXphdGlvbi1pZCJ9fQ==";
        Assertions.assertEquals(expectedValue, got, "the x-rh-identity contents were not correctly base64 encoded");
    }

    /**
     * Tests that when generating an encoded x-rh-identity content with just
     * an organization id, the content is encoded as expected.
     */
    @Test
    void generateEncodedXRhIdentityBlankAccountId() {
        final String[] blankAccountIds = {null, ""};

        for (final var accountId : blankAccountIds) {
            final String got = XRhIdentityUtils.generateEncodedXRhIdentity(accountId, "organization-id");

            final String expectedValue = "eyJpZGVudGl0eSI6eyJvcmdfaWQiOiJvcmdhbml6YXRpb24taWQifX0=";
            Assertions.assertEquals(expectedValue, got, "the x-rh-identity contents were not correctly base64 encoded");
        }
    }

    /**
     * Tests that when generating an encoded x-rh-identity content with just
     * an account number, the content is encoded as expected.
     */
    @Test
    void generateEncodedXRhIdentityBlankOrgId() {
        final String[] blankOrgIds = {null, ""};

        for (final var orgId : blankOrgIds) {
            final String got = XRhIdentityUtils.generateEncodedXRhIdentity("ebs-account-number", orgId);

            final String expectedValue = "eyJpZGVudGl0eSI6eyJhY2NvdW50X251bWJlciI6ImVicy1hY2NvdW50LW51bWJlciJ9fQ==";
            Assertions.assertEquals(expectedValue, got, "the x-rh-identity contents were not correctly base64 encoded");
        }
    }

    /**
     * Tests that when generating an encoded x-rh-identity content with neither
     * an account number nor an organization id, the content is encoded as
     * expected.
     */
    @Test
    void generateEmptyEncodedXRhIdentity() {
        final String got = XRhIdentityUtils.generateEncodedXRhIdentity(null, null);

        final String expectedValue = "eyJpZGVudGl0eSI6e319";
        Assertions.assertEquals(expectedValue, got, "the x-rh-identity contents were not correctly base64 encoded");
    }
}
