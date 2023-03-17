package com.redhat.cloud.notifications;

import io.vertx.core.json.JsonObject;

import static com.redhat.cloud.notifications.MockServerLifecycleManager.getClient;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class MockServerConfig {

    // TODO NOTIF-744 Remove this as soon as all onboarded apps include the org_id field in their Kafka messages.
    public static void mockBopOrgIdTranslation() {
        JsonObject response = new JsonObject();
        response.put("id", DEFAULT_ORG_ID);

        getClient()
                .when(request()
                        .withMethod("POST")
                        .withPath("/v2/findAccount")
                )
                .respond(response()
                        .withHeader("Content-Type", "application/json")
                        .withBody(response.toString()));
    }
}
