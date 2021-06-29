package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.auth.rhid.RHIdentityAuthMechanism;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.ClearType;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class MockServerClientConfig {
    private MockServerClient mockServerClient;

    public enum RbacAccess {
        FULL_ACCESS(TestHelpers.getFileAsString("rbac-examples/rbac_example_full_access.json")),
        READ_ACCESS(TestHelpers.getFileAsString("rbac-examples/rbac_example_read_access.json")),
        NO_ACCESS(TestHelpers.getFileAsString("rbac-examples/rbac_example_no_access.json"));

        private String payload;

        RbacAccess(String payload) {
            this.payload = payload;
        }

        public String getPayload() {
            return payload;
        }
    }

    public MockServerClientConfig(MockServerClient mockServerClient) {
        this.mockServerClient = mockServerClient;
    }

    public void addMockRbacAccess(String xRhIdentity, RbacAccess access) {
        this.mockServerClient
                .when(request()
                        .withPath("/api/rbac/v1/access/")
                        .withQueryStringParameter("application", "notifications,integrations")
                        .withHeader(RHIdentityAuthMechanism.IDENTITY_HEADER, xRhIdentity)
                )
                .respond(response()
                        .withStatusCode(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(access.getPayload()));
    }

    public void addHttpTestEndpoint(HttpRequest request, HttpResponse response, boolean secure) {
        this.mockServerClient
                .withSecure(secure)
                .when(request)
                .respond(response);
    }

    public void clearRbac() {
        this.mockServerClient.clear(request()
                .withPath("/api/rbac/v1/access/"),
                ClearType.EXPECTATIONS
        );
    }

    public void removeHttpTestEndpoint(HttpRequest request) {
        this.mockServerClient.clear(request);
    }

    public MockServerClient getMockServerClient() {
        return mockServerClient;
    }

    public String getRunningAddress() {
        return String.format("%s:%d", mockServerClient.remoteAddress().getHostName(), mockServerClient.remoteAddress().getPort());
    }

}
