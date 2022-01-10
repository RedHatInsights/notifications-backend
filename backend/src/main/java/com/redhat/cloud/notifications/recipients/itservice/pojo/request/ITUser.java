package com.redhat.cloud.notifications.recipients.itservice.pojo.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "by",
        "include"
})
public class ITUser {

    public ITUser(String accountId, boolean adminsOnly, int offset, int limit) {
        // TODO adminsOnly should be an optional field in the AllOf class
        this.by = new By(new AllOf(accountId, "enabled"), new WithPaging(offset, limit));
        this.include = new Include();
    }

    @JsonProperty("by")
    private By by;

    @JsonProperty("include")
    private Include include;

    public By getBy() {
        return by;
    }

    public void setBy(By by) {
        this.by = by;
    }

    public Include getInclude() {
        return include;
    }

    public void setInclude(Include include) {
        this.include = include;
    }
}
