package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY;

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

    @JsonProperty(access = READ_ONLY)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private LocalDateTime created;

    @JsonProperty(access = READ_ONLY)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private LocalDateTime updated;

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

    public LocalDateTime getCreated() {
        return created;
    }

    public void setCreated(LocalDateTime created) {
        this.created = created;
    }

    public LocalDateTime getUpdated() {
        return updated;
    }

    public void setUpdated(LocalDateTime updated) {
        this.updated = updated;
    }

    public Set<Application> getApplications() {
        return applications;
    }

    public void setApplications(Set<Application> applications) {
        this.applications = applications;
    }
}
