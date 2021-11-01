package com.redhat.cloud.notifications.routers.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import java.util.HashMap;
import java.util.Map;

/**
 * Values received from the user-preferences UI. The structure of this class is determined by the "name" values used in
 * `SettingsValueJsonForm` This could be further simplified when removing the "old" preferences as now each page only
 * shows a bundle and not everything.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SettingsValues {

    public static class BundleSettingsValue {
        @JsonIgnore
        public String displayName;
        public Map<String, ApplicationSettingsValue> applications = new HashMap<>();
    }

    public static class ApplicationSettingsValue {
        @JsonIgnore
        public String displayName;
        public Map<EmailSubscriptionType, Boolean> notifications = new HashMap<>();
    }

    public Map<String, BundleSettingsValue> bundles = new HashMap<>();
}
