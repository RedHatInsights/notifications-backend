package com.redhat.cloud.notifications.routers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.repositories.PayloadDetailsRepository;
import com.redhat.cloud.notifications.processors.payload.PayloadDetails;
import com.redhat.cloud.notifications.processors.payload.dto.v1.ReadPayloadDetailsDto;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.UUID;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.Constants.API_INTERNAL_NOTIFICATIONS_ENGINE;
import static io.restassured.RestAssured.given;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class PayloadDetailsResourceTest {
    @Inject
    ObjectMapper objectMapper;

    @InjectMock
    PayloadDetailsRepository payloadDetailsRepository;

    /**
     * Tests that the payload contents are properly returned from the endpoint
     * when they exist in the database.
     * @throws Exception if any unexpected error occurs.
     */
    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_ENGINE})
    void testFetchEventPayload(String basePath) throws Exception {
        // Return mocked contents from the repository.
        final PayloadDetails payloadDetails = new PayloadDetails();
        payloadDetails.setContents("Red Hat Enterprise Linux");

        Mockito.when(this.payloadDetailsRepository.findById(Mockito.any())).thenReturn(Optional.of(payloadDetails));

        // Call the endpoint under test.
        final ReadPayloadDetailsDto readPayloadDetailsDto = given()
            .when()
            .get(basePath + "/payloads/{payloadDetailsId}", UUID.randomUUID())
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(ContentType.JSON)
            .extract()
            .as(ReadPayloadDetailsDto.class);

        Assertions.assertEquals(payloadDetails.getContents(), readPayloadDetailsDto.getContents());
    }

    /**
     * Tests that a "not found" response is returned when the event does not
     * exist in the database.
     */
    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_ENGINE})
    void testFetchEventPayloadNotFound(String basePath) {
        Mockito.when(this.payloadDetailsRepository.findById(Mockito.any())).thenReturn(Optional.empty());

        // Call the endpoint under test.
        given()
            .when()
            .get(basePath + "/payloads/{payloadDetailsId}", UUID.randomUUID())
            .then()
            .statusCode(HttpStatus.SC_NOT_FOUND);
    }
}
