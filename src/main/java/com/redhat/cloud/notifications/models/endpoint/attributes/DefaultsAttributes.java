package com.redhat.cloud.notifications.models.endpoint.attributes;

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
