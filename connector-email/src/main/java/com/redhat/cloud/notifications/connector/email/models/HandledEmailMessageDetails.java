package com.redhat.cloud.notifications.connector.email.models;

import com.redhat.cloud.notifications.connector.v2.models.HandledMessageDetails;

public class HandledEmailMessageDetails extends HandledMessageDetails {
    public int totalRecipients;
    public String payloadId;
}
