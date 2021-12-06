package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.models.EmailAggregation;

public class AnsibleEmailAggregator extends AbstractEmailPayloadAggregator {

    public AnsibleEmailAggregator() {

    }

    @Override
    void processEmailAggregation(EmailAggregation aggregation) {
        throw new RuntimeException("Not implemented yet");
    }

}
