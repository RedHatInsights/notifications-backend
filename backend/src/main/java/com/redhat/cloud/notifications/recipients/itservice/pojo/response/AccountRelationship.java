package com.redhat.cloud.notifications.recipients.itservice.pojo.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class AccountRelationship {

    @JsonProperty("emails")
    public List<Email> emails;

    @JsonProperty("accountId")
    public String accountId;

    @JsonProperty("startDate")
    public String startDate;

    @JsonProperty("id")
    public String id;

    @JsonProperty("isPrimary")
    public Boolean isPrimary;
}
