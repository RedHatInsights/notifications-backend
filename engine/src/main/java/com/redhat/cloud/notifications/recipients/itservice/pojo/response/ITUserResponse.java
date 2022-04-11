package com.redhat.cloud.notifications.recipients.itservice.pojo.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ITUserResponse {

    public String id;
    public List<Authentication> authentications;
    public List<AccountRelationship> accountRelationships;
    public PersonalInformation personalInformation;
}
