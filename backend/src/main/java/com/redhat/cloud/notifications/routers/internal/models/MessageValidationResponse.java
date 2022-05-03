package com.redhat.cloud.notifications.routers.internal.models;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MessageValidationResponse {
    @NotNull
    private Map<String, List<String>> errors = new HashMap<>();

    public MessageValidationResponse() {
    }

    public Map<String, List<String>> getErrors() {
        return errors;
    }

    public void addError(String field, String message) {
        errors.computeIfAbsent(field, s -> new ArrayList<>()).add(message);
    }
}
