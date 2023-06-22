package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

import static com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;

@JsonNaming(SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DrawerEntry {

    private List<String> organizations;

    private List<String> users;

    private DrawerEntryPayload payload;

    public DrawerEntry() {
    }

    public void setOrganizations(List<String> organizations) {
        this.organizations = organizations;
    }

    public void setUsers(List<String> users) {
        this.users = users;
    }

    public DrawerEntryPayload getPayload() {
        return payload;
    }

    public void setPayload(DrawerEntryPayload payload) {
        this.payload = payload;
    }
}
