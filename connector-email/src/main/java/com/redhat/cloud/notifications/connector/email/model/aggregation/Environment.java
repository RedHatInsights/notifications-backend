package com.redhat.cloud.notifications.connector.email.model.aggregation;

public record Environment(
    String url,
    String ocmUrl,
    String applicationServicesUrl
) { }
