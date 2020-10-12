package com.redhat.cloud.notifications.models;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.UUID;

public class DefaultsAttributes extends Attributes {
    @NotNull
    ArrayList<UUID> targetEndpoints;

    public DefaultsAttributes() {

    }

    public ArrayList<UUID> getTargetEndpoints() {
        return targetEndpoints;
    }

    public void setTargetEndpoints(ArrayList<UUID> targetEndpoints) {
        this.targetEndpoints = targetEndpoints;
    }
}
