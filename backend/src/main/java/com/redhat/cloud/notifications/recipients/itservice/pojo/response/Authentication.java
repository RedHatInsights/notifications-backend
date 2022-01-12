package com.redhat.cloud.notifications.recipients.itservice.pojo.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Authentication {

    @JsonProperty("principal")
    public String principal;

    @JsonProperty("providerName")
    public String providerName;
}
