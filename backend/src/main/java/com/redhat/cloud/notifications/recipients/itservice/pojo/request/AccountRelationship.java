package com.redhat.cloud.notifications.recipients.itservice.pojo.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "allOf",
        "by"
})
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
