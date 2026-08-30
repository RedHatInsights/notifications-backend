package com.redhat.cloud.notifications.events;

import io.quarkus.test.junit.QuarkusTestProfile;

// Simple test profile class to map application-dlqtest.properties file
public class DlqTestProfile implements QuarkusTestProfile {

    @Override
    public String getConfigProfile() {
        return "dlqtest";
    }
}
