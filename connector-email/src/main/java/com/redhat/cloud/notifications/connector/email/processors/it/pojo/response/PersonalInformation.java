package com.redhat.cloud.notifications.connector.email.processors.it.pojo.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PersonalInformation {

    public String firstName;
    public String lastNames;
    public String prefix;
    public String localeCode;
    public String timeZone;
    public String rawOffset;
}
