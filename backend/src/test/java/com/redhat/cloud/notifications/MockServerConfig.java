package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.auth.rbac.workspace.WorkspaceUtils;
import jakarta.ws.rs.core.MediaType;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.mockserver.model.ClearType;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.Parameter;
import org.mockserver.verify.VerificationTimes;

import java.io.InputStream;

import static com.redhat.cloud.notifications.Constants.X_RH_IDENTITY_HEADER;
import static com.redhat.cloud.notifications.MockServerLifecycleManager.getClient;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

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
        getClient()
                .when(request()
                        .withPath("/api/rbac/v1/access/")
                        .withQueryStringParameter("application", "notifications,integrations")
                        .withHeader(X_RH_IDENTITY_HEADER, xRhIdentity)
                )
                .respond(response()
                        .withStatusCode(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(access.getPayload()));
    }

    public static void addGroupResponse(String xRhIdentity, String groupId, int statusCode) {
        getClient()
            .when(request()
                    .withPath(String.format("/api/rbac/v1/groups/%s/", groupId))
                    .withHeader(X_RH_IDENTITY_HEADER, xRhIdentity)
            )
            .respond(response()
                    .withStatusCode(statusCode)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{}")
            );
    }

    public void addHttpTestEndpoint(HttpRequest request, HttpResponse response, boolean secure) {
        getClient()
            .withSecure(secure)
            .when(request)
            .respond(response);
    }

    /**
     * Adds a path in the MockServer for the {@link com.redhat.cloud.notifications.auth.rbac.RbacWorkspacesPskClient#getWorkspaces(String, String, String, String, Integer, Integer)}
     * method, which simulates RBAC returning a response which does not have
     * the expected "count" JSON key.
     */
    public static void addMissingCountFromWorkspacesResponseRbacEndpoint() {
        getClient()
            .when(
                request()
                    .withHeader("x-rh-rbac-psk", "development-psk-value")
                    .withHeader("x-rh-rbac-client-id", WorkspaceUtils.APPLICATION_KEY)
                    .withHeader("x-rh-rbac-org-id", DEFAULT_ORG_ID)
                    .withQueryStringParameter(Parameter.param("offset", String.valueOf(WorkspaceUtils.REQUEST_DEFAULT_OFFSET)))
                    .withQueryStringParameter(Parameter.param("limit", String.valueOf(WorkspaceUtils.REQUEST_DEFAULT_LIMIT)))
                    .withPath("/api/rbac/v2/workspaces/")
            ).respond(
                response()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON)
                    .withStatusCode(HttpStatus.SC_OK)
                    .withBody(getFileAsString("rbac-examples/workspaces/missing-count-response.json"))
            );
    }

    /**
     * Adds a path in the MockServer for the {@link com.redhat.cloud.notifications.auth.rbac.RbacWorkspacesPskClient#getWorkspaces(String, String, String, String, Integer, Integer)}
     * method, which simulates RBAC returning more than one workspace in the
     * response.
     */
    public static void addMultipleReturningMultipleWorkspacesRbacEndpoint() {
        getClient()
            .when(
                request()
                    .withHeader("x-rh-rbac-psk", "development-psk-value")
                    .withHeader("x-rh-rbac-client-id", WorkspaceUtils.APPLICATION_KEY)
                    .withHeader("x-rh-rbac-org-id", DEFAULT_ORG_ID)
                    .withQueryStringParameter(Parameter.param("offset", String.valueOf(WorkspaceUtils.REQUEST_DEFAULT_OFFSET)))
                    .withQueryStringParameter(Parameter.param("limit", String.valueOf(WorkspaceUtils.REQUEST_DEFAULT_LIMIT)))
                    .withPath("/api/rbac/v2/workspaces/")
            ).respond(
                response()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON)
                    .withStatusCode(HttpStatus.SC_OK)
                    .withBody(getFileAsString("rbac-examples/workspaces/multiple-workspaces-response.json"))
            );
    }

    /**
     * Adds a path in the MockServer for the {@link com.redhat.cloud.notifications.auth.rbac.RbacWorkspacesPskClient#getWorkspaces(String, String, String, String, Integer, Integer)}
     * method, which simulates RBAC returning a default workspace in the
     * response.
     */
    public static void addMultipleReturningSingleDefaultWorkspaceRbacEndpoint() {
        getClient()
            .when(
                request()
                    .withHeader("x-rh-rbac-psk", "development-psk-value")
                    .withHeader("x-rh-rbac-client-id", WorkspaceUtils.APPLICATION_KEY)
                    .withHeader("x-rh-rbac-org-id", DEFAULT_ORG_ID)
                    .withQueryStringParameter(Parameter.param("offset", String.valueOf(WorkspaceUtils.REQUEST_DEFAULT_OFFSET)))
                    .withQueryStringParameter(Parameter.param("limit", String.valueOf(WorkspaceUtils.REQUEST_DEFAULT_LIMIT)))
                    .withPath("/api/rbac/v2/workspaces/")
            ).respond(
                response()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON)
                    .withStatusCode(HttpStatus.SC_OK)
                    .withBody(getFileAsString("rbac-examples/workspaces/single-default-workspace-response.json"))
            );
    }

    /**
     * Adds a path in the MockServer for the {@link com.redhat.cloud.notifications.auth.rbac.RbacWorkspacesPskClient#getWorkspaces(String, String, String, String, Integer, Integer)}
     * method, which simulates RBAC returning a response with no workspaces in
     * it.
     */
    public static void addNoReturnedWorkspacesResponseRbacEndpoint() {
        getClient()
            .when(
                request()
                    .withHeader("x-rh-rbac-psk", "development-psk-value")
                    .withHeader("x-rh-rbac-client-id", WorkspaceUtils.APPLICATION_KEY)
                    .withHeader("x-rh-rbac-org-id", DEFAULT_ORG_ID)
                    .withQueryStringParameter(Parameter.param("offset", String.valueOf(WorkspaceUtils.REQUEST_DEFAULT_OFFSET)))
                    .withQueryStringParameter(Parameter.param("limit", String.valueOf(WorkspaceUtils.REQUEST_DEFAULT_LIMIT)))
                    .withPath("/api/rbac/v2/workspaces/")
            ).respond(
                response()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON)
                    .withStatusCode(HttpStatus.SC_OK)
                    .withBody(getFileAsString("rbac-examples/workspaces/no-workspaces-response.json"))
            );
    }

    /**
     * Adds a path in the MockServer for the {@link com.redhat.cloud.notifications.auth.rbac.RbacWorkspacesPskClient#getWorkspaces(String, String, String, String, Integer, Integer)}
     * method, which simulates RBAC returning a "root" workspace instead of the
     * expected "default" workspace.
     */
    public static void addReturningSingleRootWorkspaceRbacEndpoint() {
        getClient()
            .when(
                request()
                    .withHeader("x-rh-rbac-psk", "development-psk-value")
                    .withHeader("x-rh-rbac-client-id", WorkspaceUtils.APPLICATION_KEY)
                    .withHeader("x-rh-rbac-org-id", DEFAULT_ORG_ID)
                    .withQueryStringParameter(Parameter.param("offset", String.valueOf(WorkspaceUtils.REQUEST_DEFAULT_OFFSET)))
                    .withQueryStringParameter(Parameter.param("limit", String.valueOf(WorkspaceUtils.REQUEST_DEFAULT_LIMIT)))
                    .withPath("/api/rbac/v2/workspaces/")
            ).respond(
                response()
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON)
                    .withStatusCode(HttpStatus.SC_OK)
                    .withBody(getFileAsString("rbac-examples/workspaces/single-root-workspace-response.json"))
            );
    }

    /**
     * Verifies that the default workspace for an organization has been only
     * fetched once from RBAC, since Notifications should have cached it.
     */
    public static void verifyDefaultWorkspaceFetchedOnlyOnce() {
        getClient()
            .verify(
                request()
                    .withHeader("x-rh-rbac-psk", "development-psk-value")
                    .withHeader("x-rh-rbac-client-id", WorkspaceUtils.APPLICATION_KEY)
                    .withHeader("x-rh-rbac-org-id", DEFAULT_ORG_ID)
                    .withQueryStringParameter(Parameter.param("offset", String.valueOf(WorkspaceUtils.REQUEST_DEFAULT_OFFSET)))
                    .withQueryStringParameter(Parameter.param("limit", String.valueOf(WorkspaceUtils.REQUEST_DEFAULT_LIMIT)))
                    .withPath("/api/rbac/v2/workspaces/"),
                VerificationTimes.once()
            );
    }

    public static void clearRbacWorkspaces() {
        getClient()
            .clear(
                request()
                .withPath("/api/rbac/v2/workspaces/")
            );
    }

    public static void clearRbac() {
        getClient().clear(request()
                .withPath("/api/rbac/v1/access/"),
                ClearType.EXPECTATIONS
        );
    }

    public static void removeHttpTestEndpoint(HttpRequest request) {
        getClient().clear(request);
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
