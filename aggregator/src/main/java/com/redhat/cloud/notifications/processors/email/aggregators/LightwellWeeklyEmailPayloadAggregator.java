package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.models.EmailAggregation;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Aggregates Lightwell instant notifications into a weekly digest payload.
 *
 * Input: EmailAggregation instances containing instant notifications with package events
 * Output: Context map compatible with weeklyEmailBody.html template containing:
 * - total_packages: total count of unique packages
 * - total_repositories: count of distinct event types
 * - packages_by_event_type: map of event types to their package lists
 */
public class LightwellWeeklyEmailPayloadAggregator extends AbstractEmailPayloadAggregator {

    // Notification common keys
    private static final String EVENT_TYPE_KEY = "event_type";
    private static final String EVENT_TYPE_DISPLAY_NAME_KEY = "event_type_display_name";
    private static final String EVENTS_KEY = "events";
    private static final String PAYLOAD_KEY = "payload";

    // Lightwell event payload keys
    private static final String PACKAGE_NAME = "package_name";
    private static final String PACKAGE_LINK = "package_link";

    // Weekly aggregation context keys
    private static final String TOTAL_PACKAGES = "total_packages";
    private static final String TOTAL_REPOSITORIES = "total_repositories";
    private static final String PACKAGES_BY_EVENT_TYPE = "packages_by_event_type";
    private static final String PACKAGES = "packages";

    // Aggregation state
    private final Map<String, EventTypePackages> packagesByEventType = new LinkedHashMap<>();

    public LightwellWeeklyEmailPayloadAggregator() {
    }

    @Override
    void processEmailAggregation(EmailAggregation aggregation) {
        JsonObject notificationPayload = aggregation.getPayload();
        String eventType = notificationPayload.getString(EVENT_TYPE_KEY);
        String eventTypeDisplayName = notificationPayload.getString(EVENT_TYPE_DISPLAY_NAME_KEY);

        if (eventType == null || eventTypeDisplayName == null) {
            return;
        }

        // Get or create event type package collection
        EventTypePackages eventTypePackages = packagesByEventType.computeIfAbsent(
            eventType,
            key -> new EventTypePackages(eventTypeDisplayName)
        );

        // Process all events in this notification
        JsonArray events = notificationPayload.getJsonArray(EVENTS_KEY);
        if (events == null) {
            Log.warnf("Skipping aggregation with missing events array [eventType=%s, eventTypeDisplayName=%s]",
                eventType, eventTypeDisplayName);
            return;
        }

        for (Object eventObject : events) {
            JsonObject event = (JsonObject) eventObject;
            JsonObject payload = event.getJsonObject(PAYLOAD_KEY);

            if (payload == null) {
                continue;
            }

            String packageName = payload.getString(PACKAGE_NAME);
            String packageLink = payload.getString(PACKAGE_LINK);

            if (packageName != null && packageLink != null) {
                eventTypePackages.addOrUpdatePackage(packageName, packageLink);
            }
        }
    }

    private void updateContext() {
        // Build the packages_by_event_type structure
        JsonObject packagesByEventTypeJson = new JsonObject();

        for (Map.Entry<String, EventTypePackages> entry : packagesByEventType.entrySet()) {
            String eventTypeKey = entry.getKey();
            EventTypePackages eventTypePackages = entry.getValue();

            JsonObject eventTypeData = new JsonObject();
            eventTypeData.put(EVENT_TYPE_DISPLAY_NAME_KEY, eventTypePackages.getEventTypeDisplayName());

            JsonArray packagesArray = new JsonArray();
            for (PackageInfo packageInfo : eventTypePackages.getPackages().values()) {
                JsonObject packageJson = new JsonObject();
                packageJson.put(PACKAGE_NAME, packageInfo.getPackageName());
                packageJson.put(PACKAGE_LINK, packageInfo.getPackageLink());
                packagesArray.add(packageJson);
            }

            eventTypeData.put(PACKAGES, packagesArray);
            packagesByEventTypeJson.put(eventTypeKey, eventTypeData);
        }

        context.put(PACKAGES_BY_EVENT_TYPE, packagesByEventTypeJson);
        context.put(TOTAL_REPOSITORIES, packagesByEventType.size());

        // Count total unique packages across all event types
        int totalPackageCount = packagesByEventType.values().stream()
            .mapToInt(eventTypePackages -> eventTypePackages.getPackages().size())
            .sum();
        context.put(TOTAL_PACKAGES, totalPackageCount);
    }

    @Override
    public Map<String, Object> getContext() {
        updateContext();
        return super.getContext();
    }

    @Override
    public boolean isEmpty() {
        return packagesByEventType.isEmpty();
    }

    /**
     * Holds packages for a single event type
     */
    private static class EventTypePackages {
        private final String eventTypeDisplayName;
        private final Map<String, PackageInfo> packages = new LinkedHashMap<>();

        EventTypePackages(String eventTypeDisplayName) {
            this.eventTypeDisplayName = eventTypeDisplayName;
        }

        void addOrUpdatePackage(String packageName, String packageLink) {
            packages.putIfAbsent(packageName, new PackageInfo(packageName, packageLink));
        }

        String getEventTypeDisplayName() {
            return eventTypeDisplayName;
        }

        Map<String, PackageInfo> getPackages() {
            return packages;
        }
    }

    /**
     * Holds information about a single package
     */
    private static class PackageInfo {
        private final String packageName;
        private final String packageLink;

        PackageInfo(String packageName, String packageLink) {
            this.packageName = packageName;
            this.packageLink = packageLink;
        }

        String getPackageName() {
            return packageName;
        }

        String getPackageLink() {
            return packageLink;
        }
    }
}
