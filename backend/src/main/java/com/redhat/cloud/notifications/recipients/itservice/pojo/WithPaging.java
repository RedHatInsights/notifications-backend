package com.redhat.cloud.notifications.recipients.itservice.pojo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "firstResultIndex",
        "maxResults"
})
public class WithPaging {

    public WithPaging(Integer firstResultIndex, Integer maxResults) {
        this.firstResultIndex = firstResultIndex;
        this.maxResults = maxResults;
    }

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
