package com.redhat.cloud.notifications.routers.models;

import javax.validation.constraints.NotNull;
import java.util.List;

public class Facet {

    @NotNull
    private final String id;

    @NotNull
    private final String name;

    @NotNull
    private final String displayName;

    private final List<Facet> children;

    // Makes jackson happy (default constructor)
    private Facet() {
        this.id = null;
        this.name = null;
        this.displayName = null;
        this.children = null;
    }

    public Facet(@NotNull String id, @NotNull String name, @NotNull String displayName, List<Facet> children) {
        this.id = id;
        this.name = name;
        this.displayName = displayName;
        this.children = children;
    }

    public Facet(@NotNull String id, @NotNull String name, @NotNull String displayName) {
        this(id, name, displayName, null);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<Facet> getChildren() {
        return children;
    }
}
