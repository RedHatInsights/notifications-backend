package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.models.EmailAggregation;
import io.vertx.core.json.JsonObject;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class AnsibleEmailAggregator extends AbstractEmailPayloadAggregator {

    public AnsibleEmailAggregator() {

    }

    @Override
    void processEmailAggregation(EmailAggregation aggregation) {
        throw new RuntimeException("Not implemented yet");
    }

}
