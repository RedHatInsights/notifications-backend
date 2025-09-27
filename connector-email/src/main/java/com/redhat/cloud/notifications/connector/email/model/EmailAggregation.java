package com.redhat.cloud.notifications.connector.email.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.redhat.cloud.notifications.connector.email.model.aggregation.ApplicationAggregatedData;
import com.redhat.cloud.notifications.connector.email.model.aggregation.Environment;
import java.util.List;

public record EmailAggregation(
    @JsonProperty("bundle_name")            String bundleName,
    @JsonProperty("bundle_display_name")    String bundleDisplayName,
    @JsonProperty("environment")            Environment environment,
    @JsonProperty("application_aggregated_data_list") List<ApplicationAggregatedData> applicationAggregatedDataList
) { }
