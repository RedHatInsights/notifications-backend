package com.redhat.cloud.notifications.rhose;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.openbridge.BridgeApiService;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;

import java.util.UUID;

import static com.redhat.cloud.notifications.MockServerConfig.mockBadRequestStatusWithBody;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class RhoseResponseExceptionMapperTest {

    @Inject
    @RestClient
    BridgeApiService bridgeApiService;

    @Test
    void test() {
        mockBadRequestStatusWithBody();
        WebApplicationException e = assertThrows(WebApplicationException.class, () -> {
            bridgeApiService.getProcessingErrors(UUID.randomUUID().toString(), "token");
        });
        assertTrue(e.getMessage().contains("abort mission"));
    }
}
