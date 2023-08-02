package com.redhat.cloud.notifications.routers.models.transformers;

import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.routers.models.SettingsValuesByEventType;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import javax.enterprise.context.ApplicationScoped;
import java.util.Map;

@ApplicationScoped
public class SettingsValuesByEventTypeTransformer {
    /**
     * Transforms the settings values of the particular application from the
     * given bundle, into a list of event types.
     * @param settingsValues            the settings values to transform.
     * @param bundleCanonicalName       the canonical name of the bundle.
     * @param applicationCanonicalName  the canonical name of the application.
     * @return a list of transformed event types from the given application
     * in JSON format, ready to be sent.
     */
    public String toJson(final SettingsValuesByEventType settingsValues, final String bundleCanonicalName, final String applicationCanonicalName) {
        final SettingsValuesByEventType.BundleSettingsValue bundleSettingsValue = settingsValues.bundles.get(bundleCanonicalName);
        final SettingsValuesByEventType.ApplicationSettingsValue applicationSettingsValue = bundleSettingsValue.applications.get(applicationCanonicalName);

        final JsonArray eventTypes = new JsonArray();
        for (final Map.Entry<String, SettingsValuesByEventType.EventTypeSettingsValue> eventTypeSettingsValueEntry : applicationSettingsValue.eventTypes.entrySet()) {
            final JsonObject eventType = this.transformEventType(
                bundleCanonicalName,
                applicationCanonicalName,
                eventTypeSettingsValueEntry.getKey(),
                eventTypeSettingsValueEntry.getValue()
            );

            eventTypes.add(eventType);
        }

        final JsonObject payload = new JsonObject();
        payload.put("eventTypes", eventTypes);

        return payload.encode();
    }

    /**
     * Transforms the given settings values to a JSON string.
     * @param settingsValues the settings values to transform.
     * @return a JSON string ready to be sent.
     */
    public String toJson(final SettingsValuesByEventType settingsValues) {
        final JsonObject bundles = new JsonObject();

        for (final Map.Entry<String, SettingsValuesByEventType.BundleSettingsValue> bundleSettingsValueEntry : settingsValues.bundles.entrySet()) {
            final String bundleCanonicalName = bundleSettingsValueEntry.getKey();

            final JsonObject applications = new JsonObject();

            for (final Map.Entry<String, SettingsValuesByEventType.ApplicationSettingsValue> applicationSettingsValueEntry : bundleSettingsValueEntry.getValue().applications.entrySet()) {
                final String applicationCanonicalName = applicationSettingsValueEntry.getKey();

                final JsonArray eventTypes = new JsonArray();

                for (final Map.Entry<String, SettingsValuesByEventType.EventTypeSettingsValue> eventTypeSettingsValueEntry : applicationSettingsValueEntry.getValue().eventTypes.entrySet()) {
                    final JsonObject eventType = this.transformEventType(
                        bundleCanonicalName,
                        applicationCanonicalName,
                        eventTypeSettingsValueEntry.getKey(),
                        eventTypeSettingsValueEntry.getValue()
                    );

                    eventTypes.add(eventType);
                }

                final JsonObject application = new JsonObject();
                application.put("label", applicationSettingsValueEntry.getValue().displayName);
                application.put("eventTypes", eventTypes);

                applications.put(applicationCanonicalName, application);
            }

            final JsonObject bundle = new JsonObject();
            bundle.put("label", bundleSettingsValueEntry.getValue().displayName);
            bundle.put("applications", applications);

            bundles.put(bundleCanonicalName, bundle);
        }

        final JsonObject payload = new JsonObject();
        payload.put("bundles", bundles);

        return payload.encode();
    }

    /**
     * Transforms the given event types to a {@link JsonObject}.
     * @param bundleCanonicalName       the canonical name of the bundle.
     * @param applicationCanonicalName  the canonical name of the application.
     * @param eventTypeCanonicalName    the canonical name of the event type.
     * @param eventTypeSettingsValue    the generated settings value for the
     *                                  event type.
     * @return a {@link JsonObject} containing the contents of the transformed
     * event types.
     */
    private JsonObject transformEventType(final String bundleCanonicalName, final String applicationCanonicalName, final String eventTypeCanonicalName, final SettingsValuesByEventType.EventTypeSettingsValue eventTypeSettingsValue) {
        final JsonObject eventType = new JsonObject();

        eventType.put("name", eventTypeCanonicalName);
        eventType.put("label", eventTypeSettingsValue.displayName);

        final JsonArray fields = new JsonArray();
        for (final Map.Entry<EmailSubscriptionType, Boolean> emailSubscriptionTypeEntry : eventTypeSettingsValue.emailSubscriptionTypes.entrySet()) {
            final JsonObject fieldCollection = this.transformEventTypeFields(
                bundleCanonicalName,
                applicationCanonicalName,
                eventTypeCanonicalName,
                emailSubscriptionTypeEntry.getKey(),
                emailSubscriptionTypeEntry.getValue(),
                eventTypeSettingsValue.hasForcedEmail
            );

            fields.add(fieldCollection);
        }

        eventType.put("fields", fields);
        return eventType;
    }

    /**
     * Transforms the fields for the event types to a {@link JsonObject}.
     * @param bundleCanonicalName       the canonical name of the bundle.
     * @param applicationCanonicalName  the canonical name of the application.
     * @param eventTypeCanonicalName    the canonical name of the event type.
     * @param emailSubscriptionType     the email subscription type to generate
     *                                  the JSON fields from.
     * @param isSubscribed              is the user subscribed to
     * @param hasForcedEmail            is the service forcing notifications?
     * @return a {@link JsonObject} containing the contents of the transformed
     * event type.
     */
    private JsonObject transformEventTypeFields(final String bundleCanonicalName, final String applicationCanonicalName, final String eventTypeCanonicalName, final EmailSubscriptionType emailSubscriptionType, final boolean isSubscribed, final boolean hasForcedEmail) {
        final JsonObject field = new JsonObject();
        field.put("name", String.format("bundles[%s].applications[%s].eventTypes[%s].emailSubscriptionTypes[%s]", bundleCanonicalName, applicationCanonicalName, eventTypeCanonicalName, emailSubscriptionType.toString()));

        switch (emailSubscriptionType) {
            case DAILY -> {
                field.put("label", "Daily digest");
                field.put("description", "Daily summary of triggered application events in 24 hours span.");
            }
            case INSTANT -> {
                field.put("label", "Instant notification");
                field.put("description", "Immediate email for each triggered application event.");
                field.put("checkedWarning", "Opting into this notification may result in a large number of emails.");
            }
            case DRAWER -> {
                field.put("label", "Drawer notification");
                field.put("description", "Drawer notification for each triggered application event.");
            }
            default -> { }
        }

        field.put("initialValue", isSubscribed);
        field.put("component", "descriptiveCheckbox");
        field.put("validate", new JsonArray());

        if (hasForcedEmail) {
            field.put("infoMessage", "You may still receive forced notifications for this service");
        }

        return field;
    }
}
