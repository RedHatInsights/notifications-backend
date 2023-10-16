package com.redhat.cloud.notifications.recipients.resolver.itservice.pojo.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Authentication {

    public String principal;
    public String providerName;
}
