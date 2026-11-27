package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.models.EmailAggregation;

import java.time.LocalDateTime;
import java.util.List;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;

public class LightwellTestHelpers {

    /**
     * Create EmailAggregation with no event type display name (simulates a payload where the display name is absent).
     */
    public static EmailAggregation createEmailAggregation(String bundle, String application, String eventType,
                                                          String packageName, String packageLink) {
        return createEmailAggregation(DEFAULT_ORG_ID, bundle, application, eventType,
            packageName, packageLink, null);
    }

    /**
     * Create EmailAggregation with explicit orgId and no event type display name.
     */
    public static EmailAggregation createEmailAggregation(String orgId, String bundle, String application,
                                                          String eventType,
                                                          String packageName, String packageLink) {
        return createEmailAggregation(orgId, bundle, application, eventType,
            packageName, packageLink, null);
    }

    /**
     * Create EmailAggregation with an explicit event type display name. The payload is built through the real
     * {@code BaseTransformer}, so the display name ends up nested at {@code source.event_type.display_name} exactly
     * as it does in production. Pass {@code null} to omit it entirely.
     */
    public static EmailAggregation createEmailAggregation(String orgId, String bundle, String application,
                                                          String eventType,
                                                          String packageName, String packageLink,
                                                          String eventTypeDisplayName) {
        EmailAggregation aggregation = new EmailAggregation();
        aggregation.setBundleName(bundle);
        aggregation.setApplicationName(application);
        aggregation.setOrgId(orgId);

        Action emailActionMessage = new Action();
        emailActionMessage.setBundle(bundle);
        emailActionMessage.setApplication(application);
        emailActionMessage.setTimestamp(LocalDateTime.now());
        emailActionMessage.setEventType(eventType);
        emailActionMessage.setContext(new Context.ContextBuilder().build());

        emailActionMessage.setEvents(List.of(
            new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(
                    new Payload.PayloadBuilder()
                        .withAdditionalProperty("package_name", packageName)
                        .withAdditionalProperty("package_link", packageLink)
                        .build()
                )
                .build()
        ));
        emailActionMessage.setOrgId(orgId);

        // Route through the real BaseTransformer so the payload matches production output: the event type display
        // name ends up nested at source.event_type.display_name, not as a top-level event_type_display_name key.
        aggregation.setPayload(TestHelpers.wrapActionToJsonObject(emailActionMessage, eventTypeDisplayName));

        return aggregation;
    }

    public static EmailAggregation createEmailAggregationMultiplePackages(String bundle, String application) {
        EmailAggregation aggregation = new EmailAggregation();
        aggregation.setBundleName(bundle);
        aggregation.setApplicationName(application);
        aggregation.setOrgId(DEFAULT_ORG_ID);

        Action emailActionMessage = new Action();
        emailActionMessage.setBundle(bundle);
        emailActionMessage.setApplication(application);
        emailActionMessage.setTimestamp(LocalDateTime.now());
        emailActionMessage.setEventType("java-remediated");
        emailActionMessage.setContext(new Context.ContextBuilder().build());

        emailActionMessage.setEvents(List.of(
            new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(
                    new Payload.PayloadBuilder()
                        .withAdditionalProperty("package_name", "org.glassfish.jaxb:codemodel")
                        .withAdditionalProperty("package_link", "https://console.redhat.com/lightwell/packages/org.glassfish.jaxb:codemodel")
                        .build()
                )
                .build(),
            new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(
                    new Payload.PayloadBuilder()
                        .withAdditionalProperty("package_name", "org.glassfish.jaxb:jaxb-core")
                        .withAdditionalProperty("package_link", "https://console.redhat.com/lightwell/packages/org.glassfish.jaxb:jaxb-core")
                        .build()
                )
                .build()
        ));
        emailActionMessage.setOrgId(DEFAULT_ORG_ID);

        // Route through the real BaseTransformer so the display name is nested under source.event_type.display_name,
        // exactly as it is in production (the Event model carries it via BaseTransformer.getEventSource).
        aggregation.setPayload(TestHelpers.wrapActionToJsonObject(emailActionMessage, "Java Remediated"));

        return aggregation;
    }
}
