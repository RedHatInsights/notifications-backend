package com.redhat.cloud.notifications;

import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpResponse;

import static org.mockserver.model.HttpRequest.request;

public class RbacConfigurator {
    private MockServerClient mockServerClient;

    public enum RbacAccess {
        FULL_ACCESS(AbstractITest.getFileAsString("rbac-examples/rbac_example_full_access.json")),
        NO_ACCESS(AbstractITest.getFileAsString("rbac-examples/rbac_example_no_access.json"));

        private String payload;

        RbacAccess(String payload) {
            this.payload = payload;
        }

        public String getPayload() {
            return payload;
        }
    }

    public RbacConfigurator(MockServerClient mockServerClient) {
        this.mockServerClient = mockServerClient;
    }

    public void addMockRbacAccess(String xRhIdentity, RbacAccess access) {
        this.mockServerClient
                .when(request()
                        .withPath("/api/rbac/v1/access/")
                        .withQueryStringParameter("application", "notifications")
                        .withHeader("x-rh-identity", xRhIdentity)
                )
                .respond(HttpResponse.response()
                        .withStatusCode(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(access.getPayload())
                );
    }
}
