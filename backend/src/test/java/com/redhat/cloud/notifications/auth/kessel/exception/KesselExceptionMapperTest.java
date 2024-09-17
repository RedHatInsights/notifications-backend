package com.redhat.cloud.notifications.auth.kessel.exception;

import com.redhat.cloud.notifications.TestConstants;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.BackendConfig;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.Header;
import io.vertx.core.json.JsonObject;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.project_kessel.relations.client.CheckClient;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class KesselExceptionMapperTest {
    @InjectMock
    BackendConfig backendConfig;

    @InjectMock
    CheckClient checkClient;

    /**
     * Tests that when a {@link io.grpc.StatusRuntimeException} is thrown in
     * our codebase, the exception is handled gracefully and an "Internal
     * server error" error is returned to the client.
     */
    @Test
    void testInternalServerErrorReturned() {
        // Enable Kessel as the authorization back end.
        Mockito.when(this.backendConfig.isKesselRelationsEnabled()).thenReturn(true);

        // Simulate that an error occurred when calling Kessel.
        Mockito.when(this.checkClient.check(Mockito.any())).thenThrow(new StatusRuntimeException(Status.INVALID_ARGUMENT));

        // Call an endpoint and verify that the response has been gracefully
        // handled.
        RestAssured.basePath = TestConstants.API_INTEGRATIONS_V_1_0;

        final String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(TestConstants.DEFAULT_ACCOUNT_ID, TestConstants.DEFAULT_ORG_ID, "Red Hat user");
        final Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        final JsonObject expectedPayload = new JsonObject();
        expectedPayload.put("error", "Internal server error");

        given()
            .header(identityHeader)
            .when()
            .get("/endpoints")
            .then()
            .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR)
            .contentType(JSON)
            .body(is(expectedPayload.encode()));
    }
}
