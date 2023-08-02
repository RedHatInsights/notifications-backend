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
}
