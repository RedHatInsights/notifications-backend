package com.redhat.cloud.notifications.recipients.rbac.pojo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "allOf",
        "withPaging"
})
public class By {

    public By(AllOf allOf, WithPaging withPaging) {
        this.allOf = allOf;
        this.withPaging = withPaging;
    }

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
