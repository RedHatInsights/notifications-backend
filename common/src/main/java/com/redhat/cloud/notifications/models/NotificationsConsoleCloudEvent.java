package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.redhat.cloud.event.parser.ConsoleCloudEvent;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@JsonIgnoreProperties(ignoreUnknown = true)
public class NotificationsConsoleCloudEvent extends ConsoleCloudEvent {

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

    public Recipients getRecipients() {
        if (recipients == null && getData().has("notification_recipients")) {
            recipients = new Recipients(getData().get("notification_recipients"));
        }

        return recipients;
    }
}
