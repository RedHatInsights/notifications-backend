package com.redhat.cloud.notifications.connector.drawer.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import java.util.Set;

import static com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;

@JsonNaming(SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DrawerEntry {

    private List<String> organizations;

    private Set<String> users;

    private DrawerEntryPayload payload;

    public void setOrganizations(List<String> organizations) {
        this.organizations = organizations;
    }

    public List<String> getOrganizations() {
        return organizations;
    }

    public void setUsers(Set<String> users) {
        this.users = users;
    }

    public Set<String> getUsers() {
        return users;
    }

    public DrawerEntryPayload getPayload() {
        return payload;
    }

    public void setPayload(DrawerEntryPayload payload) {
        this.payload = payload;
    }
}
