package com.redhat.cloud.notifications.openbridge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Map;

import static com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;

/**
 *
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class Bridge {
    /* ID of the bridge is a unique identifier */
    private String id;
    /* The endpoint CloudEvents should be sent to */
    private String endpoint;
    /* The name of the bridge. */
    private String name;

    private String status;

    private String owner;

    private String cloudProvider;

    private String region;

    private String statusMessage;

    private Map<String, Object> errorHandler;

    public Bridge() {
    }

    public Bridge(String id, String endpoint, String name) {
        this.id = id;
        this.endpoint = endpoint;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getCloudProvider() {
        return cloudProvider;
    }

    public void setCloudProvider(String cloudProvider) {
        this.cloudProvider = cloudProvider;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public Map<String, Object> getErrorHandler() {
        return errorHandler;
    }

    public void setErrorHandler(Map<String, Object> errorHandler) {
        this.errorHandler = errorHandler;
    }

    @Override
    public String toString() {
        String sb = "Bridge{" + "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", endpoint='" + endpoint + '\'' +
                '}';
        return sb;
    }
}
