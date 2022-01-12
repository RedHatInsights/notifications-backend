package com.redhat.cloud.notifications.recipients.itservice.pojo.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AllOf {

    @JsonProperty("ebsAccountNumber")
    public String ebsAccountNumber;

    @JsonProperty("status")
    public String status;

    @JsonProperty("permissionCode")
    public PermissionCode permissionCode;
}
