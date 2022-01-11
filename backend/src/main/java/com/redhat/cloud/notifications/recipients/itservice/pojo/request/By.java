package com.redhat.cloud.notifications.recipients.itservice.pojo.request;

import com.fasterxml.jackson.annotation.JsonProperty;

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
