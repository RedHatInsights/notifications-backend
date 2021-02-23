package com.redhat.cloud.notifications.routers.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.redhat.cloud.notifications.models.EmailSubscription.EmailSubscriptionType;

import java.util.HashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SettingsValues {

    public static class BundleSettingsValue {
        public String name;
        public Map<String, ApplicationSettingsValue> applications = new HashMap<>();
    }

    public static class ApplicationSettingsValue {
        public String name;
        public Map<EmailSubscriptionType, Boolean> notifications = new HashMap<>();
    }


    public Map<String, BundleSettingsValue> bundles = new HashMap<>();
}
