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
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.UUID;

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
    @Test
    void testFetchEventPayload() throws Exception {
        // Return mocked contents from the repository.
        final PayloadDetails payloadDetails = new PayloadDetails();
        payloadDetails.setContents("Red Hat Enterprise Linux");

        Mockito.when(this.payloadDetailsRepository.findById(Mockito.any())).thenReturn(Optional.of(payloadDetails));

        // Call the endpoint under test.
        final String response = given()
            .when()
            .get("/internal/payloads/{payloadDetailsId}", UUID.randomUUID())
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(ContentType.JSON)
            .extract()
            .asString();

        // Assert that the returned response contains the payload.
        final ReadPayloadDetailsDto readPayloadDetailsDto = this.objectMapper.readValue(response, ReadPayloadDetailsDto.class);

        Assertions.assertEquals(payloadDetails.getContents(), readPayloadDetailsDto.getContents());
    }

    /**
     * Tests that a "not found" response is returned when the event does not
     * exist in the database.
     */
    @Test
    void testFetchEventPayloadNotFound() {
        Mockito.when(this.payloadDetailsRepository.findById(Mockito.any())).thenReturn(Optional.empty());

        // Call the endpoint under test.
        given()
            .when()
            .get("/internal/payloads/{payloadDetailsId}", UUID.randomUUID())
            .then()
            .statusCode(HttpStatus.SC_NOT_FOUND);
    }
}
