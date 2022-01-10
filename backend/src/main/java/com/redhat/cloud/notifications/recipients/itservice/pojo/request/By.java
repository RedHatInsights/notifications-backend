package com.redhat.cloud.notifications.recipients.itservice.pojo.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "allOf",
        "withPaging"
})
public class By {

    @JsonProperty("allOf")
    private AllOf allOf;

    @JsonProperty("withPaging")
    private WithPaging withPaging;

    public AllOf getAllOf() {
        return allOf;
    }

    public void setAllOf(AllOf allOf) {
        this.allOf = allOf;
    }

    public WithPaging getWithPaging() {
        return withPaging;
    }

    public void setWithPaging(WithPaging withPaging) {
        this.withPaging = withPaging;
    }
}
