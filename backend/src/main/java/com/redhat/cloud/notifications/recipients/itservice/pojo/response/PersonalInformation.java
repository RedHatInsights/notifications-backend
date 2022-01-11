package com.redhat.cloud.notifications.recipients.itservice.pojo.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PersonalInformation {

    @JsonProperty("firstName")
    private String firstName;

    @JsonProperty("lastNames")
    private String lastNames;

    @JsonProperty("prefix")
    private String prefix;

    @JsonProperty("localeCode")
    private String localeCode;

    @JsonProperty("timeZone")
    private String timeZone;

    @JsonProperty("rawOffset")
    private String rawOffset;

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastNames() {
        return lastNames;
    }

    public void setLastNames(String lastNames) {
        this.lastNames = lastNames;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getLocaleCode() {
        return localeCode;
    }

    public void setLocaleCode(String localeCode) {
        this.localeCode = localeCode;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    public String getRawOffset() {
        return rawOffset;
    }

    public void setRawOffset(String rawOffset) {
        this.rawOffset = rawOffset;
    }
}
