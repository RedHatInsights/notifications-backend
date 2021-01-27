package com.redhat.cloud.notifications.models;

import com.redhat.cloud.notifications.ingress.Action;

public class Notification {
    private Action action;

    private final Endpoint endpoint;

    public Notification(Action action, Endpoint endpoint) {
        this.action = action;
        this.endpoint = endpoint;
    }

    public Action getAction() {
        return action;
    }

    public String getTenant() {
        return action.getAccountId();
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

}
