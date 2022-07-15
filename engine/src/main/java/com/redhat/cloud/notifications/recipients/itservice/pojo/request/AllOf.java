package com.redhat.cloud.notifications.recipients.itservice.pojo.request;

import com.fasterxml.jackson.annotation.JsonInclude;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

public class AllOf {

    @JsonInclude(NON_EMPTY)
    public String ebsAccountNumber;

    public String status;
    public PermissionCode permissionCode;
}
