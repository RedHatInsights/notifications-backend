package com.redhat.cloud.notifications.recipients.itservice.pojo.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public class WithPaging {

    @JsonProperty("firstResultIndex")
    private Integer firstResultIndex;
    @JsonProperty("maxResults")
    private Integer maxResults;

    public Integer getFirstResultIndex() {
        return firstResultIndex;
    }

    public void setFirstResultIndex(Integer firstResultIndex) {
        this.firstResultIndex = firstResultIndex;
    }

    public Integer getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(Integer maxResults) {
        this.maxResults = maxResults;
    }
}
