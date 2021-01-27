package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotNull;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

public class Application {
    private UUID id;

    @NotNull
    private String name;

    @NotNull
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String display_name;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Date created;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Date updated;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Set<EventType> eventTypes; // optional

    public Application() {

    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
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

    @JsonProperty
    public Date getCreated() {
        return created;
    }

    @JsonIgnore
    public void setCreated(Date created) {
        this.created = created;
    }

    @JsonProperty
    public Date getUpdated() {
        return updated;
    }

    @JsonIgnore
    public void setUpdated(Date updated) {
        this.updated = updated;
    }
}
