package com.redhat.cloud.notifications.recipients.itservice.pojo.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class Include {

    @JsonProperty("allOf")
    public List<String> allOf;

    @JsonProperty("accountRelationships")
    public List<AccountRelationship> accountRelationships;
}
