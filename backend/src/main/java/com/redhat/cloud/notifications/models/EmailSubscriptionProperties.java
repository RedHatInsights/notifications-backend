package com.redhat.cloud.notifications.models;

public class EmailSubscriptionProperties extends EndpointProperties {

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof EndpointProperties)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return 0;
    }
}
