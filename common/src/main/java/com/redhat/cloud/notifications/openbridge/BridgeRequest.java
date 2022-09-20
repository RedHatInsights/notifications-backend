package com.redhat.cloud.notifications.openbridge;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Request for Bridge creation / update
 *
 */
public class BridgeRequest {
    /* The name of the bridge. */
    private String name;

    @JsonProperty("cloud_provider")
    private String cloudProvider;

    private String region;


    @JsonProperty("error_handler")
    private Map<String, Object> errorHandler;

    public BridgeRequest(String name, String provider, String region) {
        this.name = name;
        this.cloudProvider = provider;
        this.region = region;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public Map<String, Object> getErrorHandler() {
        return errorHandler;
    }

    public void setErrorHandler(Map<String, Object> errorHandler) {
        this.errorHandler = errorHandler;
    }

    @Override
    public String toString() {
        String sb = "BridgeRequest{" + ", name='" + name + '\'' +
                '}';
        return sb;
    }
}
