package com.redhat.cloud.notifications.recipients.itservice.pojo.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public class By {

    @JsonProperty("allOf")
    public AllOf allOf;

    @JsonProperty("withPaging")
    public WithPaging withPaging;
}
