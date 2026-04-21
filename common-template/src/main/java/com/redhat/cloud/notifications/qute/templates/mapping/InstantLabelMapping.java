package com.redhat.cloud.notifications.qute.templates.mapping;

import com.redhat.cloud.notifications.qute.templates.InstantTemplateDetails;

public class InstantLabelMapping {

    static final InstantTemplateDetails MONTHLY_NOTIFICATION = new InstantTemplateDetails(
        "Monthly notification",
        "Monthly summary of triggered application events.",
        null);

    static final InstantTemplateDetails INSTANT_NOTIFICATION = new InstantTemplateDetails(
        "Instant notification",
        "Immediate email for each triggered application event.",
        "Opting into this notification may result in a large number of emails");

    public static InstantTemplateDetails buildInstantNotificationDescription(final String bundleName, final String applicationName, final String eventTypeName) {
        InstantTemplateDetails instantLabels = INSTANT_NOTIFICATION;
        if (Rhel.BUNDLE_NAME.equals(bundleName)) {
            if (Rhel.LIFECYCLE_APP_NAME.equals(applicationName) && Rhel.RETIRING_LIFECYCLE.equals(eventTypeName)) {
                instantLabels = MONTHLY_NOTIFICATION;
            } else if (Rhel.ROADMAP_APP_NAME.equals(applicationName) && Rhel.ROADMAP_MONTHLY_REPORT.equals(eventTypeName)) {
                instantLabels = MONTHLY_NOTIFICATION;
            }
        }
        return instantLabels;
    }

}
