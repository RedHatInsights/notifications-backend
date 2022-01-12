package com.redhat.cloud.notifications.recipients.itservice.pojo.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public class WithPaging {

    @JsonProperty("firstResultIndex")
    public Integer firstResultIndex;

    @JsonProperty("maxResults")
    public Integer maxResults;
}
