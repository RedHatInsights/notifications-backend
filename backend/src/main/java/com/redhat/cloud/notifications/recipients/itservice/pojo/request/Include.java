package com.redhat.cloud.notifications.recipients.itservice.pojo.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "allOf",
        "accountRelationships"
})
public class Include {

    @JsonProperty("allOf")
    private List<String> allOf = List.of("authentications", "personal_information");

    @JsonProperty("accountRelationships")
    private List<AccountRelationship> accountRelationships = List.of(new AccountRelationship());

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
