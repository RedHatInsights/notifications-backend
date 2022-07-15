package com.redhat.cloud.notifications.recipients.itservice.pojo.request;

import com.fasterxml.jackson.annotation.JsonInclude;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

public class ITUserRequestBy {

    @JsonInclude(NON_EMPTY)
    public String accountId;

    public AllOf allOf;
    public WithPaging withPaging;
}
