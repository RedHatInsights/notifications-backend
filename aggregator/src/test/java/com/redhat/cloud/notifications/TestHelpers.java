package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.models.EmailAggregation;
import io.vertx.core.json.JsonObject;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class TestHelpers {

    public static BaseTransformer baseTransformer = new BaseTransformer();

    public static EmailAggregation createEmailAggregation(String tenant, String bundle, String application, String policyId, String inventoryId) {
        EmailAggregation aggregation = new EmailAggregation();
        aggregation.setBundleName(bundle);
        aggregation.setApplicationName(application);
        aggregation.setAccountId(tenant);

        Action emailActionMessage = new Action();
        emailActionMessage.setBundle(bundle);
        emailActionMessage.setApplication(application);
        emailActionMessage.setTimestamp(LocalDateTime.now());
        emailActionMessage.setEventType("testEmailSubscriptionInstant");

        emailActionMessage.setContext(Map.of(
                "inventory_id", inventoryId,
                "system_check_in", "2020-08-03T15:22:42.199046",
                "display_name", "My test machine",
                "tags", List.of()
        ));
        emailActionMessage.setEvents(List.of(
                Event
                        .newBuilder()
                        .setMetadataBuilder(Metadata.newBuilder())
                        .setPayload(Map.of(
                                "policy_id", policyId,
                                "policy_name", "not-tested-name",
                                "policy_description", "not-used-desc",
                                "policy_condition", "not-used-condition"
                        ))
                        .build()
        ));

        emailActionMessage.setAccountId(tenant);

        JsonObject payload = baseTransformer.transform(emailActionMessage);
        aggregation.setPayload(payload);

        return aggregation;
    }

}
