package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.transformers.BaseTransformer;
import io.vertx.core.json.JsonObject;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;

public class InventoryTestHelpers {

    public static BaseTransformer baseTransformer = new BaseTransformer();

    public static final String displayName1 = "system 1";
    public static final String errorMessage1 = "error 1";
    public static final String displayName2 = "system 2";
    public static final String errorMessage2 = "error 2";

    private static final Map<String, String> ERROR1 = Map.of(
        "code", "VE001",
        "message", errorMessage1,
        "stack_trace", "",
        "severity", "error"
    );

    private static final Map<String, String> ERROR2 = Map.of(
        "code", "VE001",
        "message", errorMessage2,
        "stack_trace", "",
        "severity", "error"
    );

    public static EmailAggregation createEmailAggregation(String tenant, String bundle, String application, String event_name) {
        EmailAggregation aggregation = new EmailAggregation();
        aggregation.setBundleName(bundle);
        aggregation.setApplicationName(application);
        aggregation.setAccountId(tenant);
        aggregation.setOrgId(DEFAULT_ORG_ID);

        Action emailActionMessage = new Action();
        emailActionMessage.setBundle(bundle);
        emailActionMessage.setApplication(application);
        emailActionMessage.setTimestamp(LocalDateTime.now());
        emailActionMessage.setEventType("validation-error");

        emailActionMessage.setContext(
                new Context.ContextBuilder()
                        .withAdditionalProperty("event_name", event_name)
                        .build()
        );
        emailActionMessage.setEvents(List.of(
                new Event.EventBuilder()
                        .withMetadata(new Metadata.MetadataBuilder().build())
                        .withPayload(
                                new Payload.PayloadBuilder()
                                        .withAdditionalProperty("host_id", tenant)
                                        .withAdditionalProperty("display_name", displayName1)
                                        .withAdditionalProperty("insights_id", "654321")
                                        .withAdditionalProperty("error", ERROR1)
                                        .build()
                        )
                        .build(),
                new Event.EventBuilder()
                        .withMetadata(new Metadata.MetadataBuilder().build())
                        .withPayload(
                                new Payload.PayloadBuilder()
                                        .withAdditionalProperty("host_id", tenant)
                                        .withAdditionalProperty("display_name", displayName2)
                                        .withAdditionalProperty("insights_id", "654321")
                                        .withAdditionalProperty("error", ERROR2)
                                        .build()
                        )
                        .build()
        ));

        emailActionMessage.setAccountId(tenant);
        emailActionMessage.setOrgId(DEFAULT_ORG_ID);

        JsonObject payload = baseTransformer.transform(emailActionMessage);
        aggregation.setPayload(payload);

        return aggregation;
    }

    public static Action createInventoryAction(String tenant, String bundle, String application, String event_name) {
        Action emailActionMessage = new Action();
        emailActionMessage.setBundle(bundle);
        emailActionMessage.setApplication(application);
        emailActionMessage.setTimestamp(LocalDateTime.now());
        emailActionMessage.setEventType("validation-error");

        emailActionMessage.setContext(
                new Context.ContextBuilder()
                        .withAdditionalProperty("event_name", event_name)
                        .build()
        );
        emailActionMessage.setEvents(List.of(
                new Event.EventBuilder()
                        .withMetadata(new Metadata.MetadataBuilder().build())
                        .withPayload(
                                new Payload.PayloadBuilder()
                                        .withAdditionalProperty("host_id", tenant)
                                        .withAdditionalProperty("display_name", displayName1)
                                        .withAdditionalProperty("insights_id", "654321")
                                        .withAdditionalProperty("error", ERROR1)
                                        .build()
                        )
                        .build()
        ));

        emailActionMessage.setAccountId(tenant);
        emailActionMessage.setOrgId(DEFAULT_ORG_ID);

        return emailActionMessage;
    }
}
