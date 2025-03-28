package com.redhat.cloud.notifications.db.builder;

import io.quarkus.logging.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

class Parameters {

    private final Map<String, Object> parameters = new HashMap<>();

    // Only used for testing
    Map<String, Object> mapCopy() {
        return new HashMap<>(parameters);
    }

    void forEach(BiConsumer<String, Object> action) {
        Log.tracef("calling forEach with the following parameters: %s", this.parameters.toString());

        this.parameters.forEach(action);
    }

    void merge(Parameters other) {
        other.parameters.forEach(this::addParam);
    }

    void addParams(Object... params) {
        if (params == null) {
            throw new IllegalArgumentException("Params must be non null");
        }

        if (params.length % 2 != 0) {
            throw new IllegalArgumentException("Params must be an even number of arguments representing key/value pairs");
        }

        for (int i = 0; i < params.length; i += 2) {
            if (params[i] == null) {
                throw new IllegalArgumentException("Even param must be a non null object");
            }

            addParam(params[i].toString(), params[i + 1]);
        }
    }

    void addParam(String key, Object value) {
        if (parameters.containsKey(key)) {
            throw new IllegalArgumentException(String.format("Parameter [%s] already exists", key));
        }

        if (value instanceof Supplier) {
            value = ((Supplier<?>) value).get();
        }

        parameters.put(key, value);
    }

}
