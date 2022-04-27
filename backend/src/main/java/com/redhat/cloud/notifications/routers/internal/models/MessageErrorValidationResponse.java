package com.redhat.cloud.notifications.routers.internal.models;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MessageErrorValidationResponse {
    @NotNull
    private Map<String, List<String>> errors = new HashMap<>();

    public MessageErrorValidationResponse() {
    }

    public Map<String, List<String>> getErrors() {
        return errors;
    }

    public void addError(String field, String message) {
        this.errors.computeIfAbsent(field, s -> new ArrayList<>());
        this.errors.get(field).add(message);
    }
}
