package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Objects;
import java.util.UUID;

import static com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY;

// TODO NOTIF-484 Add templates ownership and restrict access to the templates.
@Entity
@Table(name = "template")
public class Template extends CreationUpdateTimestamped {

    @Id
    @GeneratedValue
    @JsonProperty(access = READ_ONLY)
    private UUID id;

    @NotNull
    @Size(max = 100)
    private String name;

    @NotNull
    private String description;

    @NotNull
    private String data;

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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof Template) {
            Template other = (Template) o;
            return Objects.equals(id, other.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
