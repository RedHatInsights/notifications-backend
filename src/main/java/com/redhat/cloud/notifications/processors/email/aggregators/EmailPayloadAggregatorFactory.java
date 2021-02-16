package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.models.EmailAggregationKey;

public class EmailPayloadAggregatorFactory {

    private EmailPayloadAggregatorFactory() {

    }

    public static AbstractEmailPayloadAggregator by(EmailAggregationKey aggregationKey) {
        if (aggregationKey.getBundle().equals("insights") && aggregationKey.getApplication().equals("policies")) {
            return new PoliciesEmailPayloadAggregator();
        }

        return null;
    }
}
