package com.redhat.cloud.notifications.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.event.parser.ConsoleCloudEventParser;
import com.redhat.cloud.event.parser.exceptions.ConsoleCloudEventParsingException;
import com.redhat.cloud.notifications.events.EventWrapper;
import com.redhat.cloud.notifications.events.EventWrapperAction;
import com.redhat.cloud.notifications.events.EventWrapperCloudEvent;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.RecipientsAuthorizationCriterion;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.NotificationsConsoleCloudEvent;
import com.redhat.cloud.notifications.transformers.BaseTransformer;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import static com.redhat.cloud.notifications.transformers.BaseTransformer.RECIPIENTS_AUTHORIZATION_CRITERION;

@ApplicationScoped
public class RecipientsAuthorizationCriterionExtractor {

    @Inject
    BaseTransformer baseTransformer;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    ActionParser actionParser;

    ConsoleCloudEventParser cloudEventParser = new ConsoleCloudEventParser();

    public RecipientsAuthorizationCriterion extract(Event event) {
        if (null == event.getEventWrapper()) {
            event.setEventWrapper(getEventWrapper(event.getPayload()));
        }
        return extract(baseTransformer.toJsonObject(event));
    }

    public RecipientsAuthorizationCriterion extract(EmailAggregation emailAggregation) {
        return extract(emailAggregation.getPayload());
    }

    private RecipientsAuthorizationCriterion extract(JsonObject data) {
        if (null != data.getJsonObject(RECIPIENTS_AUTHORIZATION_CRITERION)) {
            try {
                return objectMapper.convertValue(data.getJsonObject(RECIPIENTS_AUTHORIZATION_CRITERION), RecipientsAuthorizationCriterion.class);
            } catch (IllegalArgumentException e) {
                Log.error("Error parsing authorization criteria", e);
            }
        }
        return null;
    }

    private EventWrapper<?, ?> getEventWrapper(String payload) {
        try {
            Action action = actionParser.fromJsonString(payload);
            return new EventWrapperAction(action);
        } catch (ActionParsingException actionParseException) {
            // Try to load it as a CloudEvent
            try {
                return new EventWrapperCloudEvent(cloudEventParser.fromJsonString(payload, NotificationsConsoleCloudEvent.class));
            } catch (ConsoleCloudEventParsingException cloudEventParseException) {
                /*
                 * An exception (most likely UncheckedIOException) was thrown during the payload parsing. The message
                 * is therefore considered rejected.
                 */

                actionParseException.addSuppressed(cloudEventParseException);
                throw actionParseException;
            }
        }
    }
}
