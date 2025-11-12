package com.redhat.cloud.notifications.connector.drawer.models;

import com.redhat.cloud.notifications.connector.v2.models.HandledMessageDetails;
import java.util.Set;

public class HandledDrawerMessageDetails extends HandledMessageDetails {
    public Set<String> recipientsList;

    public HandledDrawerMessageDetails(Set<String> recipientsList) {
        this.recipientsList = recipientsList;
    }
}
