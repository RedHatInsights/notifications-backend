package com.redhat.cloud.notifications.connector.email.processors.it.pojo.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Permission {

    public String permissionCode;
    public String startDate;
    public String id;
}
