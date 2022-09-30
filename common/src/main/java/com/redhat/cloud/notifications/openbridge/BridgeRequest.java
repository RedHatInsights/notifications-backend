package com.redhat.cloud.notifications.openbridge;

import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Map;

import static com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;

/**
 * Request for Bridge creation / update
 *
 */
@JsonNaming(SnakeCaseStrategy.class)
public class BridgeRequest {
    /* The name of the bridge. */
    private String name;

    private String cloudProvider;

    private String region;


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
