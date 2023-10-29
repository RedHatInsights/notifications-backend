package com.redhat.cloud.notifications.routers.models;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonAutoDetect(fieldVisibility = Visibility.ANY)
public class SettingsValueByEventTypeJsonForm {

    // These component names depends on the values used by UserPreferences UI
    private static String COMPONENT_SUBSCRIPTION = "descriptiveCheckbox";

    @JsonAutoDetect(fieldVisibility = Visibility.ANY)
    public static class Field {
        public String name;
        public String label;
        @JsonInclude(Include.NON_NULL)
        public String description;
        @JsonInclude(Include.NON_NULL)
        public Object initialValue;
        public String component;
        @JsonInclude(Include.NON_NULL)
        public List<Object> validate;
        @JsonInclude(Include.NON_NULL)
        public List<Field> fields;
        @JsonInclude(Include.NON_NULL)
        public String checkedWarning;
        @JsonInclude(Include.NON_NULL)
        public String infoMessage;
    }

    @JsonAutoDetect(fieldVisibility = Visibility.ANY)
    public static class Bundle {
        public Map<String, Application> applications = new HashMap<>();
        public String label;
    }

    @JsonAutoDetect(fieldVisibility = Visibility.ANY)
    public static class Application {
        public List<EventType> eventTypes = new ArrayList<>();
        public String label;
    }

    @JsonAutoDetect(fieldVisibility = Visibility.ANY)
    public static class EventType {
        public String name;
        public String label;
        public List<Field> fields = new ArrayList<>();
    }

    public Map<String, Bundle> bundles = new HashMap<>();

    public static Application fromSettingsValueEventTypes(SettingsValuesByEventType eventTypeEmailSubscriptions, String bundleName, String applicationName) {
        SettingsValuesByEventType.ApplicationSettingsValue applicationSettingsValue = eventTypeEmailSubscriptions.bundles.get(bundleName).applications.get(applicationName);
        return buildEventTypes(bundleName, applicationName, applicationSettingsValue);
    }

    public static SettingsValueByEventTypeJsonForm fromSettingsValue(SettingsValuesByEventType values) {
        SettingsValueByEventTypeJsonForm form = new SettingsValueByEventTypeJsonForm();
        values.bundles.forEach((bundleName, bundleSettingsValue) -> {
            final Map<String, Application> applicationsMap = new HashMap<>();
            bundleSettingsValue.applications.forEach((applicationName, applicationSettingsValue) -> {
                final Application application = buildEventTypes(bundleName, applicationName, applicationSettingsValue);
                applicationsMap.put(applicationName, application);
            });
            final Bundle bundle = new Bundle();
            bundle.applications.putAll(applicationsMap);
            bundle.label = bundleSettingsValue.displayName;
            form.bundles.put(bundleName, bundle);
        });
        return form;
    }

    private static Application buildEventTypes(String bundleName, String applicationName, SettingsValuesByEventType.ApplicationSettingsValue applicationSettingsValue) {
        final Application application = new Application();
        application.label = applicationSettingsValue.displayName;
        applicationSettingsValue.eventTypes.forEach((eventTypeName, eventTypeSettingsValue) -> {
            EventType formEventType = new EventType();
            formEventType.name = eventTypeName;
            formEventType.label = eventTypeSettingsValue.displayName;
            eventTypeSettingsValue.emailSubscriptionTypes.forEach((subscriptionType, isSubscribed) -> {

                Field field = new Field();
                field.name = String.format("bundles[%s].applications[%s].eventTypes[%s].emailSubscriptionTypes[%s]", bundleName, applicationName, eventTypeName, subscriptionType.toString());
                field.initialValue = isSubscribed;
                field.validate = List.of();
                field.component = COMPONENT_SUBSCRIPTION;
                switch (subscriptionType) {
                    case DAILY:
                        field.label = "Daily digest";
                        field.description =  "Daily summary of triggered application events in 24 hours span.";
                        break;
                    case INSTANT:
                        field.label = "Instant notification";
                        field.description = "Immediate email for each triggered application event.";
                        field.checkedWarning = "Opting into this notification may result in a large number of emails";
                        break;
                    case DRAWER:
                        field.label = "Drawer notification";
                        field.description = "Drawer notification for each triggered application event.";
                        break;
                    default:
                        return;
                }
                if (eventTypeSettingsValue.hasForcedEmail) {
                    field.infoMessage = "You may still receive forced notifications for this service";
                }
                formEventType.fields.add(field);
            });
            application.eventTypes.add(formEventType);
        });
        return application;
    }
}
