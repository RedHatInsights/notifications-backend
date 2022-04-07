package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.openbridge.Bridge;
import org.mockserver.model.ClearType;

import java.util.Map;

import static com.redhat.cloud.notifications.MockServerLifecycleManager.getClient;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.RegexBody.regex;

public class MockServerConfig {

    public static void addOpenBridgeEndpoints(Map<String, String> auth, Bridge bridge, Map<String, String> processor) {
        String authString = Json.encode(auth);
        String bridgeString = Json.encode(bridge);
        String processorString = Json.encode(processor);

        getClient()
                .when(request()
                        .withPath("/auth/realms/event-bridge-fm/protocol/openid-connect/token"))
                .respond(response()
                        .withStatusCode(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(authString));

        getClient()
                .when(request()
                        .withPath("/api/v1/bridges/.*")
                        .withMethod("GET")
                )
                .respond(response()
                        .withStatusCode(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(bridgeString));

        // This is more specific than the next one and must come first
        getClient()
                .when(request()
                        .withPath("/events")
                        .withMethod("POST")
                        .withBody(regex(".*something-random.*"))
                )
                .respond(response()
                        .withStatusCode(500));

        getClient()
                .when(request()
                        .withPath("/events")
                        .withMethod("POST")

                )
                .respond(response()
                        .withStatusCode(200));

    }

    public static void clearOpenBridgeEndpoints(Bridge bridge) {
        getClient().clear(request()
                        .withPath("/auth/realms/event-bridge-fm/protocol/openid-connect/token"),
                ClearType.EXPECTATIONS);

        getClient().clear(request()
                        .withPath("/api/v1/bridges/" + bridge.getId()),
                ClearType.EXPECTATIONS);

        getClient().clear(request()
                        .withPath("/api/v1/bridges/" + bridge.getId() + "/processors"),
                ClearType.EXPECTATIONS);
    }
}
