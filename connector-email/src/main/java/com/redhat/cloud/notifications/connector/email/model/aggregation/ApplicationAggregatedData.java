package com.redhat.cloud.notifications.connector.email.model.aggregation;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record ApplicationAggregatedData(
    @JsonProperty("app_name")           String appName,
    @JsonProperty("aggregated_data")    Map<String, Object> aggregatedData
) { }
