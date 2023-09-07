package com.redhat.cloud.notifications.connector.email.processors.it.pojo.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ITUserResponse {

    public String id;
    public List<Authentication> authentications;
    public List<AccountRelationship> accountRelationships;
    public PersonalInformation personalInformation;
}
