package com.redhat.cloud.notifications.models.dto;

public class BundleApplicationEventTypeDTO {

    public final String bundleName;

    public final String applicationName;

    public final String eventTypeName;

    public BundleApplicationEventTypeDTO(final String bundleName, final String applicationName, final String eventTypeName) {
        this.bundleName = bundleName;
        this.applicationName = applicationName;
        this.eventTypeName = eventTypeName;
    }
}
