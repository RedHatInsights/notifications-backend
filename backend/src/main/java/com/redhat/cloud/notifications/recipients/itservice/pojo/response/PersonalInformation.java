package com.redhat.cloud.notifications.recipients.itservice.pojo.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PersonalInformation {

    @JsonProperty("firstName")
    public String firstName;

    @JsonProperty("lastNames")
    public String lastNames;

    @JsonProperty("prefix")
    public String prefix;

    @JsonProperty("localeCode")
    public String localeCode;

    @JsonProperty("timeZone")
    public String timeZone;

    @JsonProperty("rawOffset")
    public String rawOffset;
}
