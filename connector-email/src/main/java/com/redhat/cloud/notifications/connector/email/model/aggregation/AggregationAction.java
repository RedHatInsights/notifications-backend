package com.redhat.cloud.notifications.connector.email.model.aggregation;

public record AggregationAction(
    String bundle,
    AggregationActionContext context
) { }
