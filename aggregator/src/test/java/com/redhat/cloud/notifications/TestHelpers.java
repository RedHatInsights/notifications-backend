package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.events.EventWrapperAction;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.ingress.Recipient;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.transformers.BaseTransformer;
import io.vertx.core.json.JsonObject;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;


public class TestHelpers {

    public static final String HCC_LOGO_TARGET = "Logo-Red_Hat-Hybrid_Cloud_Console-A-Reverse-RGB.png";

    public static final String policyId1 = "abcd-efghi-jkl-lmn";
    public static final String policyName1 = "Foobar";
    public static final String policyId2 = "0123-456-789-5721f";
    public static final String policyName2 = "Latest foo is installed";
    public static final String eventType = "test-event-type";

    public static EmailAggregation createEmailAggregation(String orgId, String bundle, String application, String policyId, String inventory_id) {
        return createEmailAggregation(orgId, bundle, application, policyId, inventory_id, null, null);
    }

    public static EmailAggregation createEmailAggregation(String orgId, String bundle, String application, String policyId, String inventory_id, Severity severity, UUID eventTypeId) {
        return createEmailAggregation(orgId, bundle, application, policyId, inventory_id, null, severity, eventTypeId);
    }

    public static EmailAggregation createEmailAggregation(String orgId, String bundle, String application, String policyId, String inventory_id, String extraRecipient) {
        return createEmailAggregation(orgId, bundle, application, policyId, inventory_id, extraRecipient, null, null);
    }

    public static EmailAggregation createEmailAggregation(String orgId, String bundle, String application, String policyId, String inventory_id, String extraRecipient, Severity severity, UUID eventTypeId) {
        EmailAggregation aggregation = new EmailAggregation();
        aggregation.setBundleName(bundle);
        aggregation.setApplicationName(application);
        aggregation.setOrgId(orgId);
        aggregation.setSeverity(severity);
        aggregation.setEventTypeId(eventTypeId);

        Action emailActionMessage = new Action();
        emailActionMessage.setBundle(bundle);
        emailActionMessage.setApplication(application);
        emailActionMessage.setTimestamp(LocalDateTime.now());
        emailActionMessage.setEventType(eventType);

        emailActionMessage.setContext(
                new Context.ContextBuilder()
                        .withAdditionalProperty("inventory_id", inventory_id)
                        .withAdditionalProperty("system_check_in", "2020-08-03T15:22:42.199046")
                        .withAdditionalProperty("display_name", "My test machine")
                        .withAdditionalProperty("tags", List.of())
                        .build()
        );
        emailActionMessage.setEvents(List.of(
                new Event.EventBuilder()
                        .withMetadata(new Metadata.MetadataBuilder().build())
                        .withPayload(
                                new Payload.PayloadBuilder()
                                        .withAdditionalProperty("policy_id", policyId)
                                        .withAdditionalProperty("policy_name", "not-tested-name")
                                        .withAdditionalProperty("policy_description", "not-used-desc")
                                        .withAdditionalProperty("policy_condition", "not-used-condition")
                                        .build()
                        )
                        .build()
        ));

        emailActionMessage.setOrgId(orgId);

        JsonObject payload = TestHelpers.wrapActionToJsonObject(emailActionMessage);
        if (null != extraRecipient) {
            Recipient recipient = new Recipient();
            recipient.setUsers(List.of(extraRecipient));
            payload.put("recipients", List.of(recipient));
        }
        aggregation.setPayload(payload);

        return aggregation;
    }

    public static EmailAggregation createEmailAggregationFromAction(Action emailActionMessage) {
        EmailAggregation aggregation = new EmailAggregation();
        aggregation.setBundleName(emailActionMessage.getBundle());
        aggregation.setApplicationName(emailActionMessage.getApplication());
        aggregation.setOrgId(emailActionMessage.getOrgId());
        aggregation.setPayload(TestHelpers.wrapActionToJsonObject(emailActionMessage));

        return aggregation;
    }



    /**
     * Helper that wraps the given action in a
     * {@link com.redhat.cloud.notifications.models.Event} type of event and
     * returns the {@link JsonObject} representation of it. This wrapper is
     * helpful to make certain tests compliant with the signature of the
     * {@link BaseTransformer#toJsonObject(com.redhat.cloud.notifications.models.Event)}
     * function.
     * @param action the action to be wrapped and transformed.
     * @return the {@link JsonObject} representation of the wrapped action.
     */
    public static JsonObject wrapActionToJsonObject(final Action action) {
        return wrapActionToJsonObject(action, null);
    }

    public static JsonObject wrapActionToJsonObject(final Action action, final String eventTypeDisplayName) {
        com.redhat.cloud.notifications.models.Event event = new com.redhat.cloud.notifications.models.Event();
        event.setEventWrapper(new EventWrapperAction(action));
        if (eventTypeDisplayName != null) {
            event.setEventTypeDisplayName(eventTypeDisplayName);
        }
        return new BaseTransformer().toJsonObject(event);
    }

    public static EmailAggregation createEmailAggregationFromAction(Action emailActionMessage, String eventTypeDisplayName) {
        EmailAggregation aggregation = new EmailAggregation();
        aggregation.setBundleName(emailActionMessage.getBundle());
        aggregation.setApplicationName(emailActionMessage.getApplication());
        aggregation.setOrgId(emailActionMessage.getOrgId());
        aggregation.setPayload(TestHelpers.wrapActionToJsonObject(emailActionMessage, eventTypeDisplayName));
        return aggregation;
    }

}
