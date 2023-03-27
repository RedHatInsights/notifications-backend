package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ConsoleCloudEvent {

    UUID id;

    String source;

    String subject;

    LocalDateTime time;

    String type;

    @JsonProperty("dataschema")
    String dataSchema;

    JsonNode data;

    @JsonProperty("redhatorgid")
    String orgId;

    @JsonProperty("redhataccount")
    String accountId;

    @JsonIgnore
    private Recipients recipients;

    public static class Recipients {

        private static final String FIELD_ONLY_ADMINS = "only_admins";
        private static final String FIELD_IGNORE_USER_PREFERENCES = "ignore_user_preferences";
        private static final String FIELD_USERS = "users";

        private boolean onlyAdmins = false;
        private boolean ignoreUserPreferences = false;
        private List<String> users = List.of();

        public Recipients(JsonNode recipients) {
            if (recipients.has(FIELD_ONLY_ADMINS)) {
                onlyAdmins = recipients.get(FIELD_ONLY_ADMINS).asBoolean(false);
            }

            if (recipients.has(FIELD_IGNORE_USER_PREFERENCES)) {
                ignoreUserPreferences = recipients.get(FIELD_IGNORE_USER_PREFERENCES).asBoolean(false);
            }

            if (recipients.has(FIELD_USERS)) {
                users = StreamSupport
                        .stream(recipients.get(FIELD_USERS).spliterator(), false)
                        .map(JsonNode::asText)
                        .collect(Collectors.toList());
            }
        }

        public boolean isOnlyAdmins() {
            return onlyAdmins;
        }

        public boolean isIgnoreUserPreferences() {
            return ignoreUserPreferences;
        }

        public List<String> getUsers() {
            return users;
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public LocalDateTime getTime() {
        return time;
    }

    public void setTime(LocalDateTime time) {
        this.time = time;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDataSchema() {
        return dataSchema;
    }

    public void setDataSchema(String dataSchema) {
        this.dataSchema = dataSchema;
    }

    public JsonNode getData() {
        return data;
    }

    public void setData(JsonNode data) {
        this.data = data;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public Recipients getRecipients() {
        if (recipients == null && data.has("notification_recipients")) {
            recipients = new Recipients(data.get("notification_recipients"));
        }

        return recipients;
    }
}
