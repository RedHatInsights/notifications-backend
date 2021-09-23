package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.transformers.BaseTransformer;
import io.vertx.core.json.JsonObject;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class DriftTestHelpers {

    public static BaseTransformer baseTransformer = new BaseTransformer();

    public static EmailAggregation createEmailAggregation(String tenant, String bundle, String application, String baselineId, String baselineName, String inventory_id, String inventory_name) {
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
                "inventory_id", inventory_id,
                "system_check_in", "2021-07-13T15:22:42.199046",
                "display_name", inventory_name,
                "tags", List.of()
        ));
        emailActionMessage.setEvents(List.of(
                Event
                        .newBuilder()
                        .setMetadataBuilder(Metadata.newBuilder())
                        .setPayload(Map.of(
                            "baseline_id", baselineId,
                            "baseline_name", baselineName
                        ))
                        .build()
        ));

        emailActionMessage.setAccountId(tenant);

        JsonObject payload = baseTransformer.transform(emailActionMessage).await().indefinitely();
        aggregation.setPayload(payload);

        return aggregation;
    }

    public static Action createDriftAction(String tenant, String bundle, String application, String inventory_id, String inventory_name) {
        Action emailActionMessage = new Action();
        emailActionMessage.setBundle(bundle);
        emailActionMessage.setApplication(application);
        emailActionMessage.setTimestamp(LocalDateTime.now());
        emailActionMessage.setEventType("testEmailSubscriptionInstant");

        emailActionMessage.setContext(Map.of(
                "inventory_id", inventory_id,
                "system_check_in", "2021-07-13T15:22:42.199046",
                "display_name", inventory_name,
                "tags", List.of()
        ));
        emailActionMessage.setEvents(List.of(
                Event
                        .newBuilder()
                        .setMetadataBuilder(Metadata.newBuilder())
                        .setPayload(Map.of(
                            "baseline_id", "baseline_01",
                            "baseline_name", "Baseline 1"
                        ))
                        .build(),
                Event
                        .newBuilder()
                        .setMetadataBuilder(Metadata.newBuilder())
                        .setPayload(Map.of(
                            "baseline_id", "baseline_02",
                            "baseline_name", "Baseline 2"
                        ))
                        .build()
        ));

        emailActionMessage.setAccountId(tenant);

        return emailActionMessage;
    }
}
