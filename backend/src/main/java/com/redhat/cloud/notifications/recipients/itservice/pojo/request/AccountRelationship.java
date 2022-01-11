package com.redhat.cloud.notifications.recipients.itservice.pojo.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class AccountRelationship {

    @JsonProperty("allOf")
    private List<String> allOf = null;

    @JsonProperty("by")
    private By__1 by;

    public List<String> getAllOf() {
        return allOf;
    }

    public void setAllOf(List<String> allOf) {
        this.allOf = allOf;
    }

    public By__1 getBy() {
        return by;
    }

    public void setBy(By__1 by) {
        this.by = by;
    }
}
