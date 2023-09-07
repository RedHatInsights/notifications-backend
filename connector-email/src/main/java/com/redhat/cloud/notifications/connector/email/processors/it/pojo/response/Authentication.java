package com.redhat.cloud.notifications.connector.email.processors.it.pojo.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Authentication {

    public String principal;
    public String providerName;
}
