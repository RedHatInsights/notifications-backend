package com.redhat.cloud.notifications.models.endpoint.attributes;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.List;

@JsonSerialize
public class EmailSubscriptionAttributes extends Attributes {

    public static class Recipient {
        private Boolean onlyAdmins;
        private Boolean ignorePreferences;
        private String groupId;


        public Boolean getOnlyAdmins() {
            return onlyAdmins;
        }

        public void setOnlyAdmins(Boolean onlyAdmins) {
            this.onlyAdmins = onlyAdmins;
        }

        public Boolean getIgnorePreferences() {
            return ignorePreferences;
        }

        public void setIgnorePreferences(Boolean ignorePreferences) {
            this.ignorePreferences = ignorePreferences;
        }

        public String getGroupId() {
            return groupId;
        }

        public void setGroupId(String groupId) {
            this.groupId = groupId;
        }
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public List<Recipient> getRecipients() {
        return recipients;
    }

    public void setRecipients(List<Recipient> recipients) {
        this.recipients = recipients;
    }

    @JsonIgnore
    private Integer id;

    private List<Recipient> recipients;

}
