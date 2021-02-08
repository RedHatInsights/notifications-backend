package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

/**
 * A bundle is an aggregation of applications.
 */
public class Bundle {

    UUID id;

    @NotNull
    @Pattern(regexp = "[a-z][a-z_0-9-]*")
    String name;

    @NotNull
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String display_name;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Date created;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Date updated;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Set<Application> applications;

    public Bundle() {
    }

    public Bundle(String bundleName, String display_name) {
        this.name = bundleName;
        this.display_name = display_name;
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

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public Date getUpdated() {
        return updated;
    }

    public void setUpdated(Date updated) {
        this.updated = updated;
    }

    public Set<Application> getApplications() {
        return applications;
    }

    public void setApplications(Set<Application> applications) {
        this.applications = applications;
    }
}
