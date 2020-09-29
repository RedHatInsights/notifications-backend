package com.redhat.cloud.notifications.models;

import java.util.Set;

public class EventType {
    private Integer id;
    private String name;
    private String description;

    // These endpoints are set per tenant - not application!
    private Set<Endpoint> endpoints;
}
