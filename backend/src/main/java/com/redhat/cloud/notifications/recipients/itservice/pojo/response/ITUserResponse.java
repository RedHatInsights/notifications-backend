package com.redhat.cloud.notifications.recipients.itservice.pojo.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class ITUserResponse {

    @JsonProperty("id")
    public String id;

    @JsonProperty("authentications")
    public List<Authentication> authentications;

    @JsonProperty("accountRelationships")
    public List<AccountRelationship> accountRelationships;

    @JsonProperty("personalInformation")
    public PersonalInformation personalInformation;
}
