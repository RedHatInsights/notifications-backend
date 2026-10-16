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
     * Create EmailAggregation for e2e tests - does NOT add event_type_display_name to payload
     * (it will be added by the database/BaseTransformer).
     */
    public static EmailAggregation createEmailAggregation(String bundle, String application, String eventType,
                                                          String packageName, String packageLink) {
        return createEmailAggregation(DEFAULT_ORG_ID, bundle, application, eventType,
            packageName, packageLink, null);
    }

    /**
     * Create EmailAggregation with explicit orgId for e2e tests - does NOT add event_type_display_name to payload
     * (it will be added by the database/BaseTransformer).
     */
    public static EmailAggregation createEmailAggregation(String orgId, String bundle, String application,
                                                          String eventType,
                                                          String packageName, String packageLink) {
        return createEmailAggregation(orgId, bundle, application, eventType,
            packageName, packageLink, null);
    }

    /**
     * Create EmailAggregation with explicit event_type_display_name for unit tests that bypass the database.
     * Pass null for eventTypeDisplayName to skip adding it (for e2e tests).
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

        aggregation.setPayload(TestHelpers.wrapActionToJsonObject(emailActionMessage));
        // Add event_type_display_name only if explicitly provided (for unit tests)
        // For e2e tests, it will be added when events are retrieved from the database
        if (eventTypeDisplayName != null) {
            aggregation.getPayload().put("event_type_display_name", eventTypeDisplayName);
        }

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

        aggregation.setPayload(TestHelpers.wrapActionToJsonObject(emailActionMessage));
        // Add event_type_display_name which would normally come from the Event model via BaseTransformer
        aggregation.getPayload().put("event_type_display_name", "Java Remediated");

        return aggregation;
    }
}
