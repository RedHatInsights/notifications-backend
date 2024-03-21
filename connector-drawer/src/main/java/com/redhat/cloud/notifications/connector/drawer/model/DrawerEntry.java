package com.redhat.cloud.notifications.connector.drawer.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.Set;

import static com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;

@JsonNaming(SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DrawerEntry {

    private Set<String> usernames;

    private DrawerEntryPayload payload;

    public void setUsernames(Set<String> usernames) {
        this.usernames = usernames;
    }

    public Set<String> getUsernames() {
        return usernames;
    }

    public DrawerEntryPayload getPayload() {
        return payload;
    }

    public void setPayload(DrawerEntryPayload payload) {
        this.payload = payload;
    }
}
