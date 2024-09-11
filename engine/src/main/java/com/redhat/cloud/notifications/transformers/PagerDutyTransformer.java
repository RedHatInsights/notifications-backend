package com.redhat.cloud.notifications.transformers;

import com.redhat.cloud.notifications.events.EventWrapper;
import com.redhat.cloud.notifications.events.EventWrapperAction;
import com.redhat.cloud.notifications.events.EventWrapperCloudEvent;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.NotificationsConsoleCloudEvent;
import com.redhat.cloud.notifications.processors.pagerduty.PagerDutyEventAction;
import com.redhat.cloud.notifications.processors.pagerduty.PagerDutySeverity;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;


import java.time.format.DateTimeFormatter;

import static com.redhat.cloud.notifications.transformers.BaseTransformer.ACCOUNT_ID;
import static com.redhat.cloud.notifications.transformers.BaseTransformer.APPLICATION;
import static com.redhat.cloud.notifications.transformers.BaseTransformer.BUNDLE;
import static com.redhat.cloud.notifications.transformers.BaseTransformer.EVENT_TYPE;
import static com.redhat.cloud.notifications.transformers.BaseTransformer.ORG_ID;
import static com.redhat.cloud.notifications.transformers.BaseTransformer.PAYLOAD;
import static com.redhat.cloud.notifications.transformers.BaseTransformer.TIMESTAMP;

/**
 * Constructs a <a href="https://support.pagerduty.com/main/docs/pd-cef">PD-CEF</a> alert event.
 * <br>
 * The severity is set to {@link PagerDutySeverity#WARNING}, and the action to {@link PagerDutyEventAction#TRIGGER} for
 * now. The following optional fields are not set (in jq format): <code>.payload.component, .payload.class, .dedup_key, .links[], .trigger[]</code>
 * <br>
 * TODO fix PagerDuty severity and event_action
 */
@ApplicationScoped
public class PagerDutyTransformer {

    public static final String CLIENT = "client";
    public static final String CLIENT_URL = "client_url";
    public static final String CUSTOM_DETAILS = "custom_details";
    public static final String EVENT_ACTION = "event_action";
    public static final String GROUP = "group";
    public static final String SEVERITY = "severity";
    public static final String SOURCE = "source";
    public static final String SOURCE_NAMES = "source_names";
    public static final String SUMMARY = "summary";

    public static final DateTimeFormatter PD_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    public JsonObject toJsonObject(Event event) {
        EventWrapper<?, ?> eventWrapper = event.getEventWrapper();
        if (eventWrapper instanceof EventWrapperAction) {
            JsonObject message = new JsonObject();
            Action action = ((EventWrapperAction) eventWrapper).getEvent();

            message.put(EVENT_ACTION, PagerDutyEventAction.TRIGGER);
            // TODO replace with appropriate link back to monitoring client
            message.put(CLIENT, "Red Hat Hybrid Cloud Console");
            message.put(CLIENT_URL, "https://console.redhat.com");

            JsonObject payload = new JsonObject();
            payload.put(SUMMARY, event.getEventTypeDisplayName());
            payload.put(TIMESTAMP, action.getTimestamp().format(PD_DATE_TIME_FORMATTER));
            payload.put(SEVERITY, PagerDutySeverity.WARNING);
            payload.put(SOURCE, event.getApplicationDisplayName());
            payload.put(GROUP, event.getBundleDisplayName());

            JsonObject custom_details = new JsonObject();
            custom_details.put(ACCOUNT_ID, action.getAccountId());
            custom_details.put(ORG_ID, action.getOrgId());
            custom_details.put(SOURCE_NAMES, JsonObject.of(
                    APPLICATION, action.getApplication(),
                    BUNDLE, action.getBundle(),
                    EVENT_TYPE, action.getEventType()
            ));

            payload.put(CUSTOM_DETAILS, custom_details);
            message.put(PAYLOAD, payload);

            return message;
        } else if (eventWrapper instanceof EventWrapperCloudEvent) {
            // Todo: Right now are are forwarding the received event - wondering if we should create our own event and wrap (could be confusing)
            //   or extend the original object (creating a copy of the event with additional fields)
            NotificationsConsoleCloudEvent cloudEvent = ((EventWrapperCloudEvent) eventWrapper).getEvent();
            return JsonObject.mapFrom(cloudEvent);
        }

        throw new RuntimeException("Unknown event wrapper sub-type received");
    }
}
