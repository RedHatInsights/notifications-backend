package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.Json;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestConstants;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointStatus;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.openbridge.Bridge;
import com.redhat.cloud.notifications.openbridge.BridgeApiService;
import com.redhat.cloud.notifications.openbridge.BridgeItemList;
import com.redhat.cloud.notifications.openbridge.Processor;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.restassured.RestAssured;
import io.restassured.http.Header;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.inject.Inject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class EndpointReadyCheckerTest {

    @Inject
    EndpointReadyChecker endpointReadyChecker;

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    FeatureFlipper featureFlipper;

    @InjectMock
    @RestClient
    BridgeApiService bridgeApiService;

    @BeforeEach
    void beforeEach() {
        RestAssured.basePath = TestConstants.API_INTEGRATIONS_V_1_0;
        featureFlipper.setObEnabled(true);
    }

    @AfterEach
    void afterEach() {
        featureFlipper.setObEnabled(false);
    }

    private final String UNUSED = "UNUSED";
    private final String ORG_ACCOUNT_ID = "ENDPOINT_READY_CHECKER_ID";

    @Test
    public void testAddingReady() {
        final String PROCESSOR_ID = "processor-add-ready";

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(ORG_ACCOUNT_ID, ORG_ACCOUNT_ID, UNUSED);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);
        resourceHelpers.setupTransformationTemplate();

        mockBridge();
        mockAddProcessor(PROCESSOR_ID);

        Map<String, Object> jsonEndpoint = createEndpoint(identityHeader);

        assertEquals("PROVISIONING", jsonEndpoint.get("status"));

        Processor processor = new Processor(UNUSED);
        processor.setStatus("ready");
        processor.setStatus_message("Its ready!");
        mockProcessor(PROCESSOR_ID, processor);

        endpointReadyChecker.execute();
        Endpoint endpoint = resourceHelpers.getEndpoint(ORG_ACCOUNT_ID, UUID.fromString(jsonEndpoint.get("id").toString()));

        assertEquals(EndpointStatus.READY, endpoint.getStatus());
    }

    @Test
    public void testAddingFailed() {
        final String PROCESSOR_ID = "processor-add-failed";

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(ORG_ACCOUNT_ID, ORG_ACCOUNT_ID, UNUSED);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);
        resourceHelpers.setupTransformationTemplate();

        mockBridge();
        mockAddProcessor(PROCESSOR_ID);

        Map<String, Object> jsonEndpoint = createEndpoint(identityHeader);

        assertEquals("PROVISIONING", jsonEndpoint.get("status"));

        Processor processor = new Processor(UNUSED);
        processor.setStatus("failed");
        processor.setStatus_message("It failed to create :-(");
        mockProcessor(PROCESSOR_ID, processor);

        endpointReadyChecker.execute();
        Endpoint endpoint = resourceHelpers.getEndpoint(ORG_ACCOUNT_ID, UUID.fromString(jsonEndpoint.get("id").toString()));

        assertEquals(EndpointStatus.FAILED, endpoint.getStatus());
    }

    @Test
    public void testDeleting() {
        final String PROCESSOR_ID = "processor-deleting";

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(ORG_ACCOUNT_ID, ORG_ACCOUNT_ID, UNUSED);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);
        resourceHelpers.setupTransformationTemplate();

        mockBridge();
        mockAddProcessor(PROCESSOR_ID);

        Map<String, Object> jsonEndpoint = createEndpoint(identityHeader);

        assertEquals("PROVISIONING", jsonEndpoint.get("status"));

        Processor processor = new Processor(UNUSED);
        processor.setStatus("ready");
        processor.setStatus_message("It is ready!");
        mockProcessor(PROCESSOR_ID, processor);

        endpointReadyChecker.execute();

        // delete it now
        deleteEndpoint(identityHeader, jsonEndpoint.get("id").toString());

        // Verify it's marked for deletion
        Endpoint endpoint = resourceHelpers.getEndpoint(ORG_ACCOUNT_ID, UUID.fromString(jsonEndpoint.get("id").toString()));
        assertEquals(EndpointStatus.DELETING, endpoint.getStatus());

        processor.setStatus("deleted");
        processor.setStatus_message("What processor?");
        endpointReadyChecker.execute();

        endpoint = resourceHelpers.getEndpoint(ORG_ACCOUNT_ID, UUID.fromString(jsonEndpoint.get("id").toString()));
        assertNull(endpoint);
    }

    @Test
    public void multipleEndpointsAtOnce() {
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(ORG_ACCOUNT_ID, ORG_ACCOUNT_ID, UNUSED);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);
        resourceHelpers.setupTransformationTemplate();

        mockBridge();

        final String PROCESSOR_1 = "processor-multiple-1";
        final String PROCESSOR_2 = "processor-multiple-2";
        final String PROCESSOR_3 = "processor-multiple-3";
        final String PROCESSOR_4 = "processor-multiple-4";
        final String PROCESSOR_5 = "processor-multiple-5";

        mockAddProcessor(PROCESSOR_1);
        Map<String, Object> jsonEndpoint1 = createEndpoint(identityHeader);

        mockAddProcessor(PROCESSOR_2);
        Map<String, Object> jsonEndpoint2 = createEndpoint(identityHeader);

        // We are going to delete the first 2 processors - fishing their lifecycle to have them as ready.
        Processor processor1 = new Processor(UNUSED);
        processor1.setStatus("ready");
        processor1.setStatus_message("It is ready!");
        mockProcessor(PROCESSOR_1, processor1);

        Processor processor2 = new Processor(UNUSED);
        processor2.setStatus("ready");
        processor2.setStatus_message("It is ready!");
        mockProcessor(PROCESSOR_2, processor2);

        endpointReadyChecker.execute();

        deleteEndpoint(identityHeader, jsonEndpoint1.get("id").toString());
        processor1.setStatus("deleted");

        deleteEndpoint(identityHeader, jsonEndpoint2.get("id").toString());
        processor2.setStatus("deleted");

        // Continue creating other processors

        // Ready
        mockAddProcessor(PROCESSOR_3);
        Map<String, Object> jsonEndpoint3 = createEndpoint(identityHeader);
        Processor processor3 = new Processor(UNUSED);
        processor3.setStatus("ready");
        processor3.setStatus_message("It is ready!");
        mockProcessor(PROCESSOR_3, processor3);

        // Ready
        mockAddProcessor(PROCESSOR_4);
        Map<String, Object> jsonEndpoint4 = createEndpoint(identityHeader);
        Processor processor4 = new Processor(UNUSED);
        processor4.setStatus("ready");
        processor4.setStatus_message("It is ready!");
        mockProcessor(PROCESSOR_4, processor4);

        // Failed
        mockAddProcessor(PROCESSOR_5);
        Map<String, Object> jsonEndpoint5 = createEndpoint(identityHeader);
        Processor processor5 = new Processor(UNUSED);
        processor5.setStatus("failed");
        processor5.setStatus_message("It is ready!");
        mockProcessor(PROCESSOR_5, processor5);

        endpointReadyChecker.execute();

        // Endpoint1 - deleted
        assertNull(resourceHelpers.getEndpoint(ORG_ACCOUNT_ID, UUID.fromString(jsonEndpoint1.get("id").toString())));

        // Endpoint2 - deleted
        assertNull(resourceHelpers.getEndpoint(ORG_ACCOUNT_ID, UUID.fromString(jsonEndpoint2.get("id").toString())));

        // Endpoint3 - ready
        assertEquals(
                EndpointStatus.READY,
                resourceHelpers.getEndpoint(ORG_ACCOUNT_ID, UUID.fromString(jsonEndpoint3.get("id").toString())).getStatus()
        );

        // Endpoint4 - ready
        assertEquals(
                EndpointStatus.READY,
                resourceHelpers.getEndpoint(ORG_ACCOUNT_ID, UUID.fromString(jsonEndpoint4.get("id").toString())).getStatus()
        );

        // Endpoint5 - failed
        assertEquals(
                EndpointStatus.FAILED,
                resourceHelpers.getEndpoint(ORG_ACCOUNT_ID, UUID.fromString(jsonEndpoint5.get("id").toString())).getStatus()
        );
    }

    private Map<String, Object> createEndpoint(Header identityHeader) {
        CamelProperties properties = new CamelProperties();
        properties.setUrl("http://200.133.110.13");
        properties.setExtras(new HashMap<>());
        Endpoint endpoint = new Endpoint();
        endpoint.setName("Endpoint1");
        endpoint.setDescription(UNUSED);
        endpoint.setEnabled(true);
        endpoint.setType(EndpointType.CAMEL);
        endpoint.setSubType("slack");
        endpoint.setProperties(properties);

        return given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(endpoint))
                .post("/endpoints")
                .then()
                .statusCode(200)
                .extract()
                .as(Map.class);
    }

    private void deleteEndpoint(Header identityHeader, String endpointId) {
        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .delete("/endpoints/" + endpointId)
                .then()
                .statusCode(204);
    }

    private void mockBridge() {
        Bridge bridge = new Bridge("321", "http://some.events/", "my bridge");
        BridgeItemList<Bridge> bridgeList = new BridgeItemList<>();
        bridgeList.setSize(1);
        bridgeList.setTotal(1);
        List<Bridge> items = new ArrayList<>();
        items.add(bridge);
        bridgeList.setItems(items);

        Mockito.when(bridgeApiService.getBridgeByName(Mockito.anyString(), Mockito.anyString())).thenReturn(bridgeList);
    }

    private void mockAddProcessor(String processorId) {
        Processor processor = new Processor("processor");
        processor.setId(processorId);

        Mockito.when(bridgeApiService.addProcessor(
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.any())
        ).thenReturn(processor);
    }

    private void mockProcessor(String processorId, Processor processor) {
        Mockito.when(bridgeApiService.getProcessorById(
                Mockito.anyString(),
                Mockito.eq(processorId),
                Mockito.anyString()
        )).thenReturn(processor);
    }

}
