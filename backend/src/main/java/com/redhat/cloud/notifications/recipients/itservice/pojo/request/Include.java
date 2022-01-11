package com.redhat.cloud.notifications.recipients.itservice.pojo.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class Include {

    @JsonProperty("allOf")
    private List<String> allOf = null;

    @JsonProperty("accountRelationships")
    private List<AccountRelationship> accountRelationships = null;

    public List<String> getAllOf() {
        return allOf;
    }

    public void setAllOf(List<String> allOf) {
        this.allOf = allOf;
    }

    public List<AccountRelationship> getAccountRelationships() {
        return accountRelationships;
    }

    public void setAccountRelationships(List<AccountRelationship> accountRelationships) {
        this.accountRelationships = accountRelationships;
    }
}
