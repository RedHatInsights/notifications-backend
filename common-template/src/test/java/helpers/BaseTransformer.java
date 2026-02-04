package helpers;

import com.redhat.cloud.notifications.ingress.Action;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class BaseTransformer {

    // JSON property names' definition.
    public static final String ACCOUNT_ID = "account_id";
    public static final String APPLICATION = "application";
    public static final String BUNDLE = "bundle";
    public static final String CONTEXT = "context";
    public static final String DISPLAY_NAME = "display_name";
    public static final String EVENT_TYPE = "event_type";
    public static final String EVENTS = "events";
    public static final String METADATA = "metadata";
    public static final String ORG_ID = "org_id";
    public static final String PAYLOAD = "payload";
    public static final String SOURCE = "source";
    public static final String SEVERITY = "severity";
    public static final String TIMESTAMP = "timestamp";
    public static final String RECIPIENTS_AUTHORIZATION_CRITERION = "recipients_authorization_criterion";

    /**
     * Transforms the given event into a {@link JsonObject}.
     * @return a {@link JsonObject} containing the given event data.
     */
    public JsonObject toJsonObject(final Action action) {

        JsonObject message = new JsonObject();
        message.put(ACCOUNT_ID, action.getAccountId());
        message.put(APPLICATION, action.getApplication());
        message.put(BUNDLE, action.getBundle());
        message.put(CONTEXT, JsonObject.mapFrom(action.getContext()));
        message.put(EVENT_TYPE, action.getEventType());
        if (action.getRecipientsAuthorizationCriterion() != null) {
            message.put(RECIPIENTS_AUTHORIZATION_CRITERION, JsonObject.mapFrom(action.getRecipientsAuthorizationCriterion()));
        }
        message.put(
                EVENTS,
                new JsonArray(
                        action.getEvents().stream().map(
                                eventItem -> Map.of(
                                        METADATA, JsonObject.mapFrom(eventItem.getMetadata()),
                                        PAYLOAD, JsonObject.mapFrom(eventItem.getPayload())
                                )
                        ).collect(Collectors.toList())
                )
        );
        message.put(ORG_ID, action.getOrgId());
        message.put(TIMESTAMP, action.getTimestamp().toString());
        if (action.getSeverity() != null) {
            message.put(SEVERITY, action.getSeverity().toUpperCase());
        }

        final JsonObject source = getEventSource(action);

        message.put(SOURCE, source);

        return message;
    }

    public static JsonObject getEventSource(Action action) {
        final JsonObject source = new JsonObject();

        final JsonObject sourceAppDisplayName = new JsonObject();
        sourceAppDisplayName.put(DISPLAY_NAME, action.getApplication());
        source.put(APPLICATION, sourceAppDisplayName);

        final JsonObject sourceBundleDisplayName = new JsonObject();
        sourceBundleDisplayName.put(DISPLAY_NAME, action.getBundle());
        source.put(BUNDLE, sourceBundleDisplayName);

        final JsonObject sourceEventTypeDisplayName = new JsonObject();
        sourceEventTypeDisplayName.put(DISPLAY_NAME, action.getEventType());
        source.put(EVENT_TYPE, sourceEventTypeDisplayName);
        return source;
    }
}
