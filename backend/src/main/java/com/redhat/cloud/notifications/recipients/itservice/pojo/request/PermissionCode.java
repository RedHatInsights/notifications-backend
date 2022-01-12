package com.redhat.cloud.notifications.recipients.itservice.pojo.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PermissionCode {

    @JsonProperty("value")
    public String value;

    @JsonProperty("operand")
    public String operand;
}
