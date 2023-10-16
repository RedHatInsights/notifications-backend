package com.redhat.cloud.notifications.recipients.resolver.itservice.pojo.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AccountRelationship {

    public List<Email> emails;
    public String accountId;
    public String startDate;
    public String id;
    public Boolean isPrimary;
    public List<Permission> permissions;
}
