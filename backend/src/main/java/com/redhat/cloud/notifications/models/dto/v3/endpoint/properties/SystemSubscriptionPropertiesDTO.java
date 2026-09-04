package com.redhat.cloud.notifications.models.dto.v3.endpoint.properties;

import java.util.Set;
import java.util.UUID;

public final class SystemSubscriptionPropertiesDTO extends EndpointPropertiesDTO {

    private Set<UUID> groupIds;

    private boolean onlyAdmins;

    public Boolean isOnlyAdmins() {
        return onlyAdmins;
    }

    public void setOnlyAdmins(final boolean onlyAdmins) {
        this.onlyAdmins = onlyAdmins;
    }

    public Set<UUID> getGroupIds() {
        return groupIds;
    }

    public void setGroupIds(Set<UUID> groupIds) {
        this.groupIds = groupIds;
    }
}
