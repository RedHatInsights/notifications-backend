package com.redhat.cloud.notifications.processors.email;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Objects;

public class ApplicationAggregatedData {

    @JsonProperty("aggregated_data")
    Map<String, Object> aggregatedData;

    @JsonProperty("app_name")
    String appName;

    public ApplicationAggregatedData(Map<String, Object> aggregatedData, String appName) {
        this.aggregatedData = aggregatedData;
        this.appName = appName;
    }

    public Map<String, Object> getAggregatedData() {
        return aggregatedData;
    }

    public String getAppName() {
        return appName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ApplicationAggregatedData that = (ApplicationAggregatedData) o;
        return Objects.equals(aggregatedData, that.aggregatedData) && Objects.equals(appName, that.appName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(aggregatedData, appName);
    }
}
