package helpers;

import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static helpers.TestHelpers.DEFAULT_ORG_ID;

public class OcmTestHelpers {

    public static JsonObject createOcmMessage(String clusterDisplayName, String subscriptionPlan, String logDescription, String subject) {
        return createOcmMessage(clusterDisplayName, subscriptionPlan, logDescription, subject, null, Optional.empty());
    }

    public static JsonObject createOcmMessage(String clusterDisplayName, String subscriptionPlan, String logDescription, String subject, String title, Optional<Map<String, Object>> specificGlobalVars) {
        JsonObject emailActionMessage = new JsonObject();
        emailActionMessage.put("bundle", "openshift");
        emailActionMessage.put("application", "cluster-manager");
        emailActionMessage.put("timestamp", LocalDateTime.now());
        emailActionMessage.put("event_type", "testEmailSubscriptionInstant");

        emailActionMessage.put("context",
                new Context.ContextBuilder()
                        .withAdditionalProperty("system_check_in", "2021-07-13T15:22:42.199046")
                        .withAdditionalProperty("tags", List.of())
                        .withAdditionalProperty("environment_url", "http://localhost")
                        .build()
        );
        Map<String, Object> globalVars = new HashMap<>();
        globalVars.put("cluster_display_name", clusterDisplayName);
        globalVars.put("subscription_id", "2XqNHRdLNEAzshh7MkkOql6fx6I");
        globalVars.put("subscription_plan", subscriptionPlan);
        globalVars.put("log_description", logDescription);
        globalVars.put("internal_cluster_id", "fekelklflef");

        if (specificGlobalVars.isPresent()) {
            globalVars.putAll(specificGlobalVars.get());
        }

        Event event = new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(
                        new Payload.PayloadBuilder()
                                .withAdditionalProperty("global_vars", globalVars)
                                .withAdditionalProperty("subject", subject)
                                .withAdditionalProperty("title", title)
                                .build()
                )
                .build();

        emailActionMessage.put("events", JsonArray.of(
                Map.of(
                        "metadata", JsonObject.mapFrom(event.getMetadata()),
                        "payload", JsonObject.mapFrom(event.getPayload())
                )
        ));

        emailActionMessage.put("org_id", DEFAULT_ORG_ID);
        emailActionMessage.put("source", JsonObject.of(
                "bundle", JsonObject.of("display_name", "OpenShift"),
                "application", JsonObject.of("display_name", "Cluster Manager"),
                "event_type", JsonObject.of("display_name", "Test instant email subscription")
        ));

        return emailActionMessage;
    }
}
