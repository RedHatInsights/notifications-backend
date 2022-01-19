package com.redhat.cloud.notifications.recipients.itservice.pojo.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Email {

    public String address;
    public Boolean isPrimary;
    public String id;
    public Boolean isConfirmed;
    public String status;
}
