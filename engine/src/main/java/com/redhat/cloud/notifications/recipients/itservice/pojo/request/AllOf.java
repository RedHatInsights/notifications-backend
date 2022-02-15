package com.redhat.cloud.notifications.recipients.itservice.pojo.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AllOf {

    public String ebsAccountNumber;
    public String status;
    public PermissionCode permissionCode;
}
