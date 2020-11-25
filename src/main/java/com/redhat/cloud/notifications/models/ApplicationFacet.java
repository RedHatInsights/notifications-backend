package com.redhat.cloud.notifications.models;

import javax.validation.constraints.NotNull;

public class ApplicationFacet {
    @NotNull
    private final String label;
    @NotNull
    private final String value;

    public ApplicationFacet(@NotNull String label, @NotNull String value) {
        this.label = label;
        this.value = value;
    }

    public String getLabel() {
        return label;
    }

    public String getValue() {
        return value;
    }
}
