package com.redhat.cloud.notifications.routers.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;

import java.util.HashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SettingsValues {

    public static class BundleSettingsValue {
        @JsonIgnore
        public String name;
        public Map<String, ApplicationSettingsValue> applications = new HashMap<>();
    }

    public static class ApplicationSettingsValue {
        @JsonIgnore
        public String name;
        public Map<EmailSubscriptionType, Boolean> notifications = new HashMap<>();
    }


    public Map<String, BundleSettingsValue> bundles = new HashMap<>();
}
