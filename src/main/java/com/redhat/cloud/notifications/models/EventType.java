package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.util.Set;

public class EventType {
    private Integer id;

    @Pattern(regexp = "[a-z][a-z_0-9-]*")
    @NotNull
    private String name;

    @NotNull
    private String display_name;

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

    public String getDisplay_name() {
        return display_name;
    }

    public void setDisplay_name(String display_name) {
        this.display_name = display_name;
    }

    public Application getApplication() {
        return application;
    }

    public void setApplication(Application application) {
        this.application = application;
    }
}
