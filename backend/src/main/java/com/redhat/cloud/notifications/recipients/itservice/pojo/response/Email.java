package com.redhat.cloud.notifications.recipients.itservice.pojo.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Email {

    @JsonProperty("address")
    public String address;

    @JsonProperty("isPrimary")
    public Boolean isPrimary;

    @JsonProperty("id")
    public String id;

    @JsonProperty("isConfirmed")
    public Boolean isConfirmed;

    @JsonProperty("status")
    public String status;
}
