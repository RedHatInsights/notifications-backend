package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.models.EmailAggregation;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;

public class ApplicationServicesTestHelpers {

    public static final String BUNDLE = "subscription-services";
    public static final String APPLICATION = "application-services";

    public static Action createApplicationServicesAction(String eventType, List<Event> events) {
        Action action = new Action();
        action.setBundle(BUNDLE);
        action.setApplication(APPLICATION);
        action.setTimestamp(LocalDateTime.of(2024, 10, 3, 15, 22, 13, 25));
        action.setEventType(eventType);
        action.setRecipients(List.of());

        action.setContext(
            new Context.ContextBuilder()
                .build()
        );

        action.setEvents(events);
        action.setAccountId(StringUtils.EMPTY);
        action.setOrgId(DEFAULT_ORG_ID);

        return action;
    }

    public static Action createKeycloakReleasesAction() {
        return createApplicationServicesAction(
            "keycloak-releases",
            List.of(
                new Event.EventBuilder()
                    .withMetadata(new Metadata.MetadataBuilder().build())
                    .withPayload(
                        new Payload.PayloadBuilder()
                            .withAdditionalProperty("download_path", "jbossnetwork/restricted/softwareDetail.html?softwareId=108766")
                            .withAdditionalProperty("description", "Red Hat build of Keycloak 26.2.13 Maven Repository")
                            .withAdditionalProperty("version", "26.2.13")
                            .build()
                    )
                    .build(),
                new Event.EventBuilder()
                    .withMetadata(new Metadata.MetadataBuilder().build())
                    .withPayload(
                        new Payload.PayloadBuilder()
                            .withAdditionalProperty("download_path", "jbossnetwork/restricted/softwareDetail.html?softwareId=108767")
                            .withAdditionalProperty("description", "Red Hat build of Keycloak 26.2.13 Server")
                            .withAdditionalProperty("version", "26.2.13")
                            .build()
                    )
                    .build()
            )
        );
    }

    public static Action createEapReleasesAction() {
        return createApplicationServicesAction(
            "eap-releases",
            List.of(
                new Event.EventBuilder()
                    .withMetadata(new Metadata.MetadataBuilder().build())
                    .withPayload(
                        new Payload.PayloadBuilder()
                            .withAdditionalProperty("download_path", "jbossnetwork/restricted/softwareDetail.html?softwareId=108920")
                            .withAdditionalProperty("description", "Red Hat JBoss Enterprise Application Platform 8.1 Update 05 Installation Manager")
                            .withAdditionalProperty("version", "8.1")
                            .build()
                    )
                    .build(),
                new Event.EventBuilder()
                    .withMetadata(new Metadata.MetadataBuilder().build())
                    .withPayload(
                        new Payload.PayloadBuilder()
                            .withAdditionalProperty("download_path", "jbossnetwork/restricted/softwareDetail.html?softwareId=108917")
                            .withAdditionalProperty("description", "Red Hat JBoss Enterprise Application Platform 8.1 Update 05 Source Code")
                            .withAdditionalProperty("version", "8.1")
                            .build()
                    )
                    .build(),
                new Event.EventBuilder()
                    .withMetadata(new Metadata.MetadataBuilder().build())
                    .withPayload(
                        new Payload.PayloadBuilder()
                            .withAdditionalProperty("download_path", "jbossnetwork/restricted/softwareDetail.html?softwareId=108918")
                            .withAdditionalProperty("description", "Red Hat JBoss Enterprise Application Platform 8.1 Update 05 Maven Repository")
                            .withAdditionalProperty("version", "8.1")
                            .build()
                    )
                    .build()
            )
        );
    }

    public static Action createActionWithNoContext() {
        Action action = new Action();
        action.setBundle(BUNDLE);
        action.setApplication(APPLICATION);
        action.setTimestamp(LocalDateTime.of(2024, 10, 3, 15, 22, 13, 25));
        action.setEventType("keycloak-releases");
        action.setRecipients(List.of());

        action.setContext(null);

        action.setEvents(List.of(
            new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(
                    new Payload.PayloadBuilder()
                        .withAdditionalProperty("download_path", "downloads/999")
                        .withAdditionalProperty("description", "Some release")
                        .withAdditionalProperty("version", "1.0")
                        .build()
                )
                .build()
        ));

        action.setAccountId(StringUtils.EMPTY);
        action.setOrgId(DEFAULT_ORG_ID);

        return action;
    }

    public static Action createActionWithNoFamily() {
        Action action = new Action();
        action.setBundle(BUNDLE);
        action.setApplication(APPLICATION);
        action.setTimestamp(LocalDateTime.of(2024, 10, 3, 15, 22, 13, 25));
        action.setEventType("unknown-releases");
        action.setRecipients(List.of());

        action.setContext(
            new Context.ContextBuilder()
                .build()
        );

        action.setEvents(List.of(
            new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(
                    new Payload.PayloadBuilder()
                        .withAdditionalProperty("download_path", "downloads/999")
                        .withAdditionalProperty("description", "Some release")
                        .withAdditionalProperty("version", "1.0")
                        .build()
                )
                .build()
        ));

        action.setAccountId(StringUtils.EMPTY);
        action.setOrgId(DEFAULT_ORG_ID);

        return action;
    }

    public static EmailAggregation createAggregationWithNullPayloadEvent() {
        // Build JSON directly because BaseTransformer cannot serialize null payloads
        JsonObject payload = new JsonObject();
        payload.put("event_type", "keycloak-releases");
        payload.put("context", new JsonObject());
        payload.put("source", new JsonObject()
            .put("event_type", new JsonObject()
                .put("display_name", "Red Hat build of Keycloak")));
        payload.put("events", new JsonArray()
            .add(new JsonObject().put("payload", new JsonObject()
                .put("download_path", "jbossnetwork/restricted/softwareDetail.html?softwareId=108766")
                .put("description", "Red Hat build of Keycloak 26.2.13 Maven Repository")
                .put("version", "26.2.13")))
            .add(new JsonObject().putNull("payload")));

        EmailAggregation aggregation = new EmailAggregation();
        aggregation.setBundleName(BUNDLE);
        aggregation.setApplicationName(APPLICATION);
        aggregation.setOrgId(DEFAULT_ORG_ID);
        aggregation.setPayload(payload);
        return aggregation;
    }

    public static Action createActionWithEmptyEvents() {
        return createApplicationServicesAction(
            "keycloak-releases",
            List.of()
        );
    }

    public static Action createActionWithNoEventType() {
        Action action = new Action();
        action.setBundle(BUNDLE);
        action.setApplication(APPLICATION);
        action.setTimestamp(LocalDateTime.of(2024, 10, 3, 15, 22, 13, 25));
        action.setEventType(null);
        action.setRecipients(List.of());

        action.setContext(
            new Context.ContextBuilder()
                .build()
        );

        action.setEvents(List.of(
            new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(
                    new Payload.PayloadBuilder()
                        .withAdditionalProperty("download_path", "downloads/999")
                        .withAdditionalProperty("description", "Some release")
                        .withAdditionalProperty("version", "1.0")
                        .build()
                )
                .build()
        ));

        action.setAccountId(StringUtils.EMPTY);
        action.setOrgId(DEFAULT_ORG_ID);

        return action;
    }
}
