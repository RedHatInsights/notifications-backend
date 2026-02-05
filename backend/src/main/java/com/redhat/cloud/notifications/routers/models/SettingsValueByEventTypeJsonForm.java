package com.redhat.cloud.notifications.routers.models;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.redhat.cloud.notifications.Severity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        public boolean disabled;
        @JsonInclude(Include.NON_NULL)
        public LinkedHashSet<SeverityDetails> severities;
    }

    @JsonAutoDetect(fieldVisibility = Visibility.ANY)
    public static class SeverityDetails {
        public String name;
        public boolean initialValue;
        public boolean disabled;
    }

    @JsonAutoDetect(fieldVisibility = Visibility.ANY)
    public static class Bundle {
        public Map<String, Application> applications = new LinkedHashMap<>();
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

    public Map<String, Bundle> bundles = new LinkedHashMap<>();

    public static Application fromSettingsValueEventTypes(SettingsValuesByEventType eventTypeEmailSubscriptions, String bundleName, String applicationName) {
        SettingsValuesByEventType.ApplicationSettingsValue applicationSettingsValue = eventTypeEmailSubscriptions.bundles.get(bundleName).applications.get(applicationName);
        return buildEventTypes(bundleName, applicationName, applicationSettingsValue);
    }

    public static SettingsValueByEventTypeJsonForm fromSettingsValue(SettingsValuesByEventType values) {
        SettingsValueByEventTypeJsonForm form = new SettingsValueByEventTypeJsonForm();
        values.bundles.forEach((bundleName, bundleSettingsValue) -> {
            final Map<String, Application> applicationsMap = new LinkedHashMap<>();
            bundleSettingsValue.applications.forEach((applicationName, applicationSettingsValue) -> {
                final Application application = buildEventTypes(bundleName, applicationName, applicationSettingsValue);
                applicationsMap.put(applicationName, application);
            });
            Map<String, Application> sortedApplicationsMap = applicationsMap.entrySet().stream()
                .sorted(Comparator.comparing(app -> app.getValue().label.toUpperCase()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a1, a2) -> a1, LinkedHashMap::new));
            final Bundle bundle = new Bundle();
            bundle.applications.putAll(sortedApplicationsMap);
            bundle.label = bundleSettingsValue.displayName;
            form.bundles.put(bundleName, bundle);
        });

        form.bundles = form.bundles.entrySet().stream()
            .sorted(Comparator.comparing(app -> app.getValue().label.toUpperCase()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (b1, b2) -> b1, LinkedHashMap::new));
        return form;
    }

    private static Application buildEventTypes(String bundleName, String applicationName, SettingsValuesByEventType.ApplicationSettingsValue applicationSettingsValue) {
        final Application application = new Application();
        application.label = applicationSettingsValue.displayName;
        List<EventType> eventTypes = new ArrayList<>();
        applicationSettingsValue.eventTypes.forEach((eventTypeName, eventTypeSettingsValue) -> {
            EventType formEventType = new EventType();
            formEventType.name = eventTypeName;
            formEventType.label = eventTypeSettingsValue.displayName;
            eventTypeSettingsValue.subscriptionTypes.forEach((subscriptionType, subscriptionTypeDetails) -> {

                boolean subscribed = false;
                LinkedHashSet<SeverityDetails> severities = new LinkedHashSet<>();
                if (subscriptionTypeDetails != null && !subscriptionTypeDetails.isEmpty()) {
                    subscribed = subscriptionTypeDetails.entrySet().stream().anyMatch(Map.Entry::getValue);

                    // sort severity Map according Severity enum order
                    for (Severity severity : Severity.values()) {
                        SeverityDetails severityDetails = new SeverityDetails();
                        severityDetails.name = severity.name();
                        severityDetails.initialValue = (subscriptionTypeDetails.get(severity) != null && subscriptionTypeDetails.get(severity));
                        severityDetails.disabled = eventTypeSettingsValue.availableSeverities == null || !eventTypeSettingsValue.availableSeverities.contains(severity);
                        severities.add(severityDetails);
                    }
                } else {
                    subscribed = eventTypeSettingsValue.emailSubscriptionTypes.get(subscriptionType);
                }

                Field field = new Field();
                field.name = String.format("bundles[%s].applications[%s].eventTypes[%s].emailSubscriptionTypes[%s]", bundleName, applicationName, eventTypeName, subscriptionType.toString());
                field.initialValue = subscribed;
                field.severities = severities;
                field.validate = List.of();
                field.component = COMPONENT_SUBSCRIPTION;
                field.disabled = eventTypeSettingsValue.subscriptionLocked;
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

            formEventType.fields = formEventType.fields.stream().sorted(Comparator.comparing(eventType -> eventType.label, Comparator.reverseOrder())).toList();
            eventTypes.add(formEventType);
        });
        application.eventTypes =
            eventTypes.stream().sorted(Comparator.comparing(evt -> evt.label)).collect(Collectors.toList());
        return application;
    }
}
