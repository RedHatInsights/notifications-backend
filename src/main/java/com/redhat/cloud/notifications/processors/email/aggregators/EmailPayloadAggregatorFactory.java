package com.redhat.cloud.notifications.processors.email.aggregators;

public class EmailPayloadAggregatorFactory {

    private EmailPayloadAggregatorFactory() {

    }

    public static EmailPayloadAggregator by(String application) {
        switch (application.toLowerCase()) {
            case "policies":
                return new PoliciesEmailPayloadAggregator();
            default:
                return null;
        }
    }
}
