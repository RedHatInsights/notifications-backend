package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.processors.email.aggregators.PatchEmailPayloadAggregator;
import com.redhat.cloud.notifications.transformers.BaseTransformer;
import io.vertx.core.json.JsonObject;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PatchTestHelpers {

    public static BaseTransformer baseTransformer = new BaseTransformer();

    private static final String ADVISORY_NAME = "advisory_name";
    private static final String ADVISORY_TYPE = "advisory_type";
    private static final String UNIQUE_HOSTS_CNT = "unique_system_count";
    private static final String ADVISORIES_KEY = "advisories";

    public static EmailAggregation createEmailAggregation(String tenant, String bundle, String application, String advisoryName, String advisoryType, String inventory_id) {
        EmailAggregation aggregation = new EmailAggregation();
        aggregation.setBundleName(bundle);
        aggregation.setApplicationName(application);
        aggregation.setAccountId(tenant);

        Action emailActionMessage = new Action();
        emailActionMessage.setBundle(bundle);
        emailActionMessage.setApplication(application);
        emailActionMessage.setTimestamp(LocalDateTime.now());
        emailActionMessage.setEventType("new-advisory");
        emailActionMessage.setContext(
                new Context.ContextBuilder()
                        .withAdditionalProperty("inventory_id", inventory_id)
                        .build()
        );
        emailActionMessage.setEvents(List.of(
                new Event.EventBuilder()
                        .withMetadata(new Metadata.MetadataBuilder().build())
                        .withPayload(
                                new Payload.PayloadBuilder()
                                        .withAdditionalProperty(ADVISORY_NAME, advisoryName)
                                        .withAdditionalProperty(ADVISORY_TYPE, advisoryType)
                                        .build()
                        )
                        .build()
        ));
        emailActionMessage.setAccountId(tenant);

        JsonObject payload = baseTransformer.transform(emailActionMessage);
        aggregation.setPayload(payload);

        return aggregation;
    }

    public static EmailAggregation createEmailAggregationMultipleEvents(String tenant, String bundle, String application) {
        EmailAggregation aggregation = new EmailAggregation();
        aggregation.setBundleName(bundle);
        aggregation.setApplicationName(application);
        aggregation.setAccountId(tenant);

        Action emailActionMessage = new Action();
        emailActionMessage = new Action();
        emailActionMessage.setBundle(bundle);
        emailActionMessage.setApplication(application);
        emailActionMessage.setTimestamp(LocalDateTime.now());
        emailActionMessage.setEventType("new-advisory");
        emailActionMessage.setContext(
                new Context.ContextBuilder()
                        .withAdditionalProperty("inventory_id", "inventory_id")
                        .build()
        );
        emailActionMessage.setEvents(List.of(
                new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(
                        new Payload.PayloadBuilder()
                                .withAdditionalProperty(ADVISORY_NAME, "RH-1")
                                .withAdditionalProperty(ADVISORY_TYPE, "ENHANCEMENT")
                                .build()
                )
                .build(),
                new Event.EventBuilder()
                        .withMetadata(new Metadata.MetadataBuilder().build())
                        .withPayload(
                                new Payload.PayloadBuilder()
                                        .withAdditionalProperty(ADVISORY_NAME, "RH-2")
                                        .withAdditionalProperty(ADVISORY_TYPE, "BUGFIX")
                                        .build()
                        )
                .build(),
                new Event.EventBuilder()
                        .withMetadata(new Metadata.MetadataBuilder().build())
                        .withPayload(
                                new Payload.PayloadBuilder()
                                        .withAdditionalProperty(ADVISORY_NAME, "RH-3")
                                        .withAdditionalProperty(ADVISORY_TYPE, "UNKNOWN")
                                        .build()
                        )
                .build()
        ));
        emailActionMessage.setAccountId(tenant);

        JsonObject payload = baseTransformer.transform(emailActionMessage);
        aggregation.setPayload(payload);

        return aggregation;
    }

    public static Integer getUniqueHostForAdvisoryType(PatchEmailPayloadAggregator aggregator, String advisoryType) {
        Map<String, Map> patch = (Map<String, Map>) aggregator.getContext().get("patch");
        return (Integer) patch.get(advisoryType).get(UNIQUE_HOSTS_CNT);
    }

    public static Integer getUniqueHost(PatchEmailPayloadAggregator aggregator) {
        return (Integer) aggregator.getContext().get(UNIQUE_HOSTS_CNT);
    }

    public static Set<String> getAdvisoriesByType(PatchEmailPayloadAggregator aggregator, String advisoryType) {
        Map<String, Object> patch = (Map<String, Object>) ((Map<String, Object>) aggregator.getContext()).get("patch");
        Map<String, Object> advisories = (Map<String, Object>) ((Map<String, Object>) patch.get(advisoryType)).get(ADVISORIES_KEY);
        return advisories.keySet();
    }
}
