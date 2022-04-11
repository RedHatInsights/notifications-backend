package com.redhat.cloud.notifications.recipients.itservice.pojo.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Permission {

    public String permissionCode;
    public String startDate;
    public String id;
}
