package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotNull;
import java.util.Set;

public class EventType {
    private Integer id;

    @NotNull
    private String name;

    @NotNull
    private String description;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Application application;

    // These endpoints are set per tenant - not application!
    // optional
    @JsonInclude(JsonInclude.Include.NON_NULL)
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

    public Application getApplication() {
        return application;
    }

    public void setApplication(Application application) {
        this.application = application;
    }
}
