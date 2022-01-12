package com.redhat.cloud.notifications.recipients.itservice.pojo.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class AccountRelationship {

    @JsonProperty("allOf")
    public List<String> allOf;

    @JsonProperty("by")
    public By__1 by;
}
