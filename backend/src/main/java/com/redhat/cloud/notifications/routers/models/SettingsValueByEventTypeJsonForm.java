package com.redhat.cloud.notifications.routers.models;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonAutoDetect(fieldVisibility = Visibility.ANY)
public class SettingsValueByEventTypeJsonForm {

    private static final ObjectMapper mapper = new ObjectMapper();

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
    }

    @JsonAutoDetect(fieldVisibility = Visibility.ANY)
    public static class Application {
        public Map<String, EventTypes> applications = new HashMap<>();
    }

    @JsonAutoDetect(fieldVisibility = Visibility.ANY)
    public static class EventTypes {
        public List<EventType> eventTypes = new ArrayList<>();
    }

    @JsonAutoDetect(fieldVisibility = Visibility.ANY)
    public static class EventType {
        public String name;
        public String label;
        public List<Field> fields = new ArrayList<>();
    }

    public Map<String, Application> bundles = new HashMap<>();

    public static EventTypes fromSettingsValueEventTypes(SettingsValuesByEventType eventTypeEmailSubscriptions, String bundleName, String applicationName) {
        SettingsValuesByEventType.ApplicationSettingsValue applicationSettingsValue = eventTypeEmailSubscriptions.bundles.get(bundleName).applications.get(applicationName);
        return buildEventTypes(bundleName, applicationName, applicationSettingsValue);
    }

    public static SettingsValueByEventTypeJsonForm fromSettingsValue(SettingsValuesByEventType values) {
        SettingsValueByEventTypeJsonForm form = new SettingsValueByEventTypeJsonForm();
        values.bundles.forEach((bundleName, bundleSettingsValue) -> {
            Map<String, EventTypes> applicationsMap = new HashMap<>();
            bundleSettingsValue.applications.forEach((applicationName, applicationSettingsValue) -> {
                EventTypes formEventTypes = buildEventTypes(bundleName, applicationName, applicationSettingsValue);
                applicationsMap.put(applicationName, formEventTypes);
            });
            Application application = new Application();
            application.applications.putAll(applicationsMap);
            form.bundles.put(bundleName, application);
        });
        return form;
    }

    private static EventTypes buildEventTypes(String bundleName, String applicationName, SettingsValuesByEventType.ApplicationSettingsValue applicationSettingsValue) {
        EventTypes formEventTypes = new EventTypes();
        applicationSettingsValue.eventTypes.forEach((eventTypeName, eventTypeSettingsValue) -> {
            EventType formEventType = new EventType();
            formEventType.name = eventTypeName;
            formEventType.label = eventTypeSettingsValue.displayName;
            eventTypeSettingsValue.emailSubscriptionTypes.forEach((emailSubscriptionType, isSubscribed) -> {

                Field field = new Field();
                field.name = String.format("bundles[%s].applications[%s].eventTypes[%s].emailSubscriptionTypes[%s]", bundleName, applicationName, eventTypeName, emailSubscriptionType.toString());
                field.initialValue = isSubscribed;
                field.validate = List.of();
                field.component = COMPONENT_SUBSCRIPTION;
                switch (emailSubscriptionType) {
                    case DAILY:
                        field.label = "Daily digest";
                        field.description =  "Daily summary of triggered application events in 24 hours span. See notification settings for configuration.";
                        break;
                    case INSTANT:
                        field.label = "Instant notification";
                        field.description = "Immediate email for each triggered application event. See notification settings for configuration.";
                        field.checkedWarning = "Opting into this notification may result in a large number of emails";
                        break;
                    default:
                        return;
                }
                formEventType.fields.add(field);
            });
            formEventTypes.eventTypes.add(formEventType);
        });
        return formEventTypes;
    }
}
