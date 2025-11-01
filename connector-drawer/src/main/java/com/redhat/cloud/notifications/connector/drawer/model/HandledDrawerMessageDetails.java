package com.redhat.cloud.notifications.connector.drawer.model;

import com.redhat.cloud.notifications.connector.v2.pojo.HandledMessageDetails;
import java.util.Set;

public class HandledDrawerMessageDetails extends HandledMessageDetails {
    public Set<String> recipientsList;

    public HandledDrawerMessageDetails(Set<String> recipientsList) {
        this.recipientsList = recipientsList;
    }
}
