package com.redhat.cloud.notifications.routers.models;

import javax.validation.constraints.NotNull;

public class Facet {

    @NotNull
    private final String id;

    @NotNull
    private final String name;

    @NotNull
    private final String displayName;

    public Facet(@NotNull String id, @NotNull String name, @NotNull String displayName) {
        this.id = id;
        this.name = name;
        this.displayName = displayName;
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
}
