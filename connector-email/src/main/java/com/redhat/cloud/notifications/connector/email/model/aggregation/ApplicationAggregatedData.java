package com.redhat.cloud.notifications.connector.email.model.aggregation;

import java.util.Map;
import java.util.Objects;

public class ApplicationAggregatedData {
    public Map<String, Object> aggregatedData;
    public String appName;

    public ApplicationAggregatedData(Map<String, Object> aggregatedData, String appName) {
        this.aggregatedData = aggregatedData;
        this.appName = appName;
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
