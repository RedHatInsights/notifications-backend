package com.redhat.cloud.notifications.connector.email.model.aggregation;

import java.util.List;

public record AggregationActionContext(
    String title,
    List<DailyDigestSection> items,
    String orgId
) { }
