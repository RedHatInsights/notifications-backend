package com.redhat.cloud.notifications.models;

import javax.validation.constraints.NotNull;
import java.util.Set;

public class EventType {
    private Integer id;

    @NotNull
    private String name;

    @NotNull
    private String description;

    // These endpoints are set per tenant - not application!
    // optional
    private Set<Endpoint> endpoints;

    public EventType() {

    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
