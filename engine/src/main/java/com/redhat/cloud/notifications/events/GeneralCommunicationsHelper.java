package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.ingress.Recipient;
import com.redhat.cloud.notifications.models.CompositeEndpointType;
import com.redhat.cloud.notifications.models.Endpoint;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

public class GeneralCommunicationsHelper {
    public static final String GENERAL_COMMUNICATION_CONTEXT_INTEGRATION_CATEGORY = "integration_category";
    public static final String GENERAL_COMMUNICATION_METADATA_KEY = "communication-description";
    public static final String GENERAL_COMMUNICATION_METADATA_VALUE = "General communication about customers' Microsoft Teams' URLs needing a review";
    public static final String GENERAL_COMMUNICATION_PAYLOAD_INTEGRATION_NAMES = "integration_names";
    public static final String GENERAL_COMMUNICATIONS_BUNDLE = "console";
    public static final String GENERAL_COMMUNICATIONS_APPLICATION = "integrations";
    public static final String GENERAL_COMMUNICATIONS_EVENT_TYPE = "general-communication";

    private GeneralCommunicationsHelper() { }

    public static Action createGeneralCommunicationAction(final String orgId, final CompositeEndpointType integrationType, final List<String> integrationNames) {
        // Add the generic data.
        final Action generalCommunicationAction = new Action();
        generalCommunicationAction.setBundle(GENERAL_COMMUNICATIONS_BUNDLE);
        generalCommunicationAction.setApplication(GENERAL_COMMUNICATIONS_APPLICATION);
        generalCommunicationAction.setEventType(GENERAL_COMMUNICATIONS_EVENT_TYPE);
        generalCommunicationAction.setOrgId(orgId);
        generalCommunicationAction.setTimestamp(LocalDateTime.now(Clock.systemUTC()));

        // Override the recipients' settings for this action, since we want
        // everyone to receive this action.
        final Recipient recipient = new Recipient();
        recipient.setIgnoreUserPreferences(true);
        generalCommunicationAction.setRecipients(List.of(recipient));

        // Add the integration's category in the action so that we can provide
        // the customers with the proper link to the type of integrations we
        // are talking about.
        final Endpoint endpoint = new Endpoint();
        endpoint.setType(integrationType.getType());
        endpoint.setSubType(integrationType.getSubType());

        final Context context = new Context();
        context.setAdditionalProperty(GENERAL_COMMUNICATION_CONTEXT_INTEGRATION_CATEGORY, IntegrationDisabledNotifier.getFrontendCategory(endpoint));
        generalCommunicationAction.setContext(context);

        // Create a particular event.
        final Event generalCommunicationEvent = new Event();

        // Specify what the general communication is about in the metadata.
        final Metadata metadata = new Metadata();
        metadata.setAdditionalProperty(GENERAL_COMMUNICATION_METADATA_KEY, GENERAL_COMMUNICATION_METADATA_VALUE);
        generalCommunicationEvent.setMetadata(metadata);

        // Include the payload.
        final Payload payload = new Payload();
        payload.setAdditionalProperty(GENERAL_COMMUNICATION_PAYLOAD_INTEGRATION_NAMES, integrationNames);
        generalCommunicationEvent.setPayload(payload);

        generalCommunicationAction.setEvents(List.of(generalCommunicationEvent));

        return generalCommunicationAction;
    }
}
