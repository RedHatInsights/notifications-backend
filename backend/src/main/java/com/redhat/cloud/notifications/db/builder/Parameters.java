package com.redhat.cloud.notifications.db.builder;

import java.util.HashMap;
import java.util.Map;

class Parameters {

    private final Map<String, Object> parameters = new HashMap<>();

    // Only used for testing
    Map<String, Object> mapCopy() {
        return new HashMap<>(parameters);
    }

    void forEach(java.util.function.BiConsumer<String, Object> action) {
        this.parameters.forEach(action);
    }

    void merge(Parameters other) {
        other.parameters.forEach(this::addParam);
    }

    void addParams(Object... params) {
        if (params.length % 2 != 0) {
            throw new IllegalArgumentException("Odd number of params. It needs to be a pair number of params. The key followed by the value");
        }

        for (int i = 0; i < params.length; i += 2) {
            addParam(params[i].toString(), params[i + 1]);
        }
    }

    void addParam(String key, Object value) {
        if (parameters.containsKey(key)) {
            throw new IllegalArgumentException(String.format("Parameter [%s] already exists", key));
        }
        parameters.put(key, value);
    }

}
