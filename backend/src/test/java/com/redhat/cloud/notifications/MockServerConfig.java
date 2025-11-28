package com.redhat.cloud.notifications;

import org.apache.commons.io.IOUtils;

import java.io.InputStream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.redhat.cloud.notifications.Constants.X_RH_IDENTITY_HEADER;
import static com.redhat.cloud.notifications.MockServerLifecycleManager.getClient;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.fail;

public class MockServerConfig {

    public enum RbacAccess {
        FULL_ACCESS(getFileAsString("rbac-examples/rbac_example_full_access.json")),
        NOTIFICATIONS_READ_ACCESS_ONLY(getFileAsString("rbac-examples/rbac_example_events_notifications_read_access_only.json")),
        NOTIFICATIONS_ACCESS_ONLY(getFileAsString("rbac-examples/rbac_example_events_notifications_access_only.json")),
        READ_ACCESS(getFileAsString("rbac-examples/rbac_example_read_access.json")),
        NO_ACCESS(getFileAsString("rbac-examples/rbac_example_no_access.json"));

        private final String payload;

        RbacAccess(String payload) {
            this.payload = payload;
        }

        public String getPayload() {
            return payload;
        }
    }

    public static void addMockRbacAccess(String xRhIdentity, RbacAccess access) {
        getClient().stubFor(
            get(urlPathEqualTo("/api/rbac/v1/access/"))
                .withQueryParam("application", equalTo("notifications,integrations"))
                .withHeader(X_RH_IDENTITY_HEADER, equalTo(xRhIdentity))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(access.getPayload()))
        );
    }

    public static void addGroupResponse(String xRhIdentity, String groupId, int statusCode) {
        getClient().stubFor(
            get(urlPathEqualTo(String.format("/api/rbac/v1/groups/%s/", groupId)))
                .withHeader(X_RH_IDENTITY_HEADER, equalTo(xRhIdentity))
                .willReturn(aResponse()
                    .withStatus(statusCode)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{}"))
        );
    }

    public void addHttpTestEndpoint(com.github.tomakehurst.wiremock.http.Request request, com.github.tomakehurst.wiremock.http.Response response, boolean secure) {
        // Note: WireMock doesn't have a direct equivalent to withSecure()
        // HTTPS is typically configured at the server level, not per-stub
        // For now, we'll just add the stub without the secure flag
        getClient().stubFor(
            any(urlMatching(request.getUrl()))
                .willReturn(aResponse()
                    .withStatus(response.getStatus()))
        );
    }

    public static void clearRbacWorkspaces() {
        // Reset request journal to clear verification history
        getClient().resetRequests();

        // Remove stub mappings for RBAC workspaces endpoint
        getClient().getStubMappings().stream()
            .filter(stub -> {
                var urlPath = stub.getRequest().getUrlPath();
                return urlPath != null && urlPath.equals("/api/rbac/v2/workspaces/");
            })
            .forEach(stub -> getClient().removeStubMapping(stub));
    }

    public static void clearRbac() {
        // Reset request journal to clear verification history
        getClient().resetRequests();

        // Remove stub mappings for RBAC access endpoint
        getClient().getStubMappings().stream()
            .filter(stub -> {
                var urlPath = stub.getRequest().getUrlPath();
                return urlPath != null && urlPath.equals("/api/rbac/v1/access/");
            })
            .forEach(stub -> getClient().removeStubMapping(stub));
    }

    public static void removeHttpTestEndpoint(com.github.tomakehurst.wiremock.http.Request request) {
        // Reset request journal to clear verification history
        getClient().resetRequests();

        // Remove stub mappings matching the request URL
        String requestUrl = request.getUrl();
        getClient().getStubMappings().stream()
            .filter(stub -> {
                var urlPattern = stub.getRequest().getUrlPattern();
                var urlPath = stub.getRequest().getUrlPath();
                return (urlPattern != null && requestUrl.matches(urlPattern)) ||
                       (urlPath != null && urlPath.equals(requestUrl));
            })
            .forEach(stub -> getClient().removeStubMapping(stub));
    }

    private static String getFileAsString(String filename) {
        try {
            InputStream is = MockServerConfig.class.getClassLoader().getResourceAsStream(filename);
            return IOUtils.toString(is, UTF_8);
        } catch (Exception e) {
            fail("Failed to read rhid example file: " + e.getMessage());
            return "";
        }
    }
}
