package com.redhat.cloud.notifications.connector.v2;

import io.smallrye.reactive.messaging.ce.IncomingCloudEventMetadata;
import io.vertx.core.json.JsonObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class MessageContext {
    private IncomingCloudEventMetadata<JsonObject> incomingCloudEventMetadata;
    private Map<String, Optional<String>> headers = new HashMap<>();
    private final Map<String, Object> properties = new HashMap<>();

    public <T> T getTypedBody(Class<T> type) {
        return incomingCloudEventMetadata.getData().mapTo(type);
    }

    public Map<String, Optional<String>> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, Optional<String>> headers) {
        this.headers = headers;
    }

    public Object getProperty(String key) {
        return properties.get(key);
    }

    public <T> T getProperty(String key, Class<T> type) {
        Object value = properties.get(key);
        return type.cast(value);
    }

    public void setProperty(String key, Object value) {
        properties.put(key, value);
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public IncomingCloudEventMetadata<JsonObject> getIncomingCloudEventMetadata() {
        return incomingCloudEventMetadata;
    }

    public void setIncomingCloudEventMetadata(IncomingCloudEventMetadata<JsonObject> incomingCloudEventMetadata) {
        this.incomingCloudEventMetadata = incomingCloudEventMetadata;
    }
}
