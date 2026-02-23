package com.redhat.cloud.notifications.exports;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Integration test for ExportServiceOidc that verifies OIDC authentication headers
 * are properly added to HTTP requests. The Export Service mock returns 401 for
 * missing/invalid Authorization headers and 200 for correct ones.
 */
@QuarkusTest
@QuarkusTestResource(OidcServerMockResource.class)
@QuarkusTestResource(ExportServiceServerMockResource.class)
public class ExportServiceOidcTest {

    @Inject
    @RestClient
    ExportServiceOidc exportServiceClient;

    private static final UUID TEST_EXPORT_REQUEST_UUID = UUID.randomUUID();
    private static final String TEST_APPLICATION = "urn:redhat:application:notifications";
    private static final UUID TEST_RESOURCE_UUID = UUID.randomUUID();

    @Test
    @DisplayName("Should successfully call uploadJSONExport with OIDC authentication")
    void shouldSuccessfullyCallUploadJSONExport() {
        // If OIDC headers are missing, the mock server will return 401 and this will throw an exception
        // If OIDC headers are present and correct, the mock server returns 200 and this succeeds
        assertDoesNotThrow(() -> exportServiceClient.uploadJSONExport(
            TEST_EXPORT_REQUEST_UUID,
            TEST_APPLICATION,
            TEST_RESOURCE_UUID,
            "{\"test\": \"data\"}"
        ));
    }

    @Test
    @DisplayName("Should successfully call uploadCSVExport with OIDC authentication")
    void shouldSuccessfullyCallUploadCSVExport() {
        // If OIDC headers are missing, the mock server will return 401 and this will throw an exception
        // If OIDC headers are present and correct, the mock server returns 200 and this succeeds
        assertDoesNotThrow(() -> exportServiceClient.uploadCSVExport(
            TEST_EXPORT_REQUEST_UUID,
            TEST_APPLICATION,
            TEST_RESOURCE_UUID,
            "header1,header2\nvalue1,value2"
        ));
    }

    @Test
    @DisplayName("Should successfully call notifyErrorExport with OIDC authentication")
    void shouldSuccessfullyCallNotifyErrorExport() {
        // If OIDC headers are missing, the mock server will return 401 and this will throw an exception
        // If OIDC headers are present and correct, the mock server returns 200 and this succeeds
        ExportError exportError = new ExportError(400, "Test error message");
        assertDoesNotThrow(() -> exportServiceClient.notifyErrorExport(
            TEST_EXPORT_REQUEST_UUID,
            TEST_APPLICATION,
            TEST_RESOURCE_UUID,
            exportError
        ));
    }
}
