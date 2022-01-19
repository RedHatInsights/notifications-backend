package com.redhat.cloud.notifications.recipients.itservice.pojo.response;

import java.util.List;

public class ITUserResponse {

    public String id;
    public List<Authentication> authentications;
    public List<AccountRelationship> accountRelationships;
    public PersonalInformation personalInformation;
}
