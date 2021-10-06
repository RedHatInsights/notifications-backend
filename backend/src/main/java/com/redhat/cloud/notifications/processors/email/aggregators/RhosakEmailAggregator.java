package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.models.EmailAggregation;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RhosakEmailAggregator extends AbstractEmailPayloadAggregator {
    private static final String UPGRADES = "upgrades";
    private static final String SERVICE_DISRUPTIONS = "disruptions";
    private static final String EVENT_TYPE = "event_type";
    private static final String SCHEDULED_UPGRADE_EVENT_TYPE = "scheduled-upgrade";
    private static final String SERVICE_DISRUPTION_EVENT_TYPE = "disruption";
    private static final String CONTEXT = "context";
    private static final String EVENTS = "events";
    private static final String PAYLOAD = "payload";

    private static final String PAYLOAD_NAME = "name";
    private static final String KAFKA_VERSION = "kafka_version";
    private static final String SERVICE_DISRUPTION_IMPACT = "impacted_area";

    // know service disruption impacts
    private static final String PERFORMANCE = "performance";
    private static final String LATENCY = "latency";
    private static final String THROUGHPUT = "throughput";

    public RhosakEmailAggregator() {
        context.put(UPGRADES, new JsonObject());
        context.put(SERVICE_DISRUPTIONS, new JsonObject());
    }

    @Override
    void processEmailAggregation(EmailAggregation aggregation) {
        JsonObject aggregationPayload = aggregation.getPayload();
        String eventType = aggregationPayload.getString(EVENT_TYPE);
        JsonObject context = aggregationPayload.getJsonObject(CONTEXT);

        if (SCHEDULED_UPGRADE_EVENT_TYPE.equals(eventType)) {
            buildUpgradesPayload(aggregationPayload, context);
        } else if (SERVICE_DISRUPTION_EVENT_TYPE.equals(eventType)) {
            buildServiceDisruptionPayload(aggregationPayload, context);
        }
    }

    private void buildServiceDisruptionPayload(JsonObject aggregationPayload, JsonObject context) {
        String serviceDisruptionImpact = context.getString(SERVICE_DISRUPTION_IMPACT);
        boolean currentDisruptionImpactPerformance = serviceDisruptionImpact.contains(PERFORMANCE);
        boolean currentDisruptionImpactLatency = serviceDisruptionImpact.contains(LATENCY);
        boolean currentDisruptionImpactTroughPut = serviceDisruptionImpact.contains(THROUGHPUT);

        JsonObject disruptions = this.context.getJsonObject(SERVICE_DISRUPTIONS);
        aggregationPayload.getJsonArray(EVENTS).stream().forEach(eventObject -> {
            JsonObject event = (JsonObject) eventObject;
            JsonObject payload = event.getJsonObject(PAYLOAD);
            String name = payload.getString(PAYLOAD_NAME);
            JsonObject serviceDisruption;
            if (!disruptions.containsKey(name)) {
                serviceDisruption = new JsonObject();
                serviceDisruption.put(PAYLOAD_NAME, name);
                serviceDisruption.put(SERVICE_DISRUPTION_IMPACT, serviceDisruptionImpact);
                disruptions.put(name, serviceDisruption);
            } else {
                serviceDisruption = disruptions.getJsonObject(name);
                String existingImpacts = serviceDisruption.getString(SERVICE_DISRUPTION_IMPACT, "");
                boolean hasPerformanceImpact = existingImpacts.contains(PERFORMANCE);
                boolean hasLatencyImpact = existingImpacts.contains(LATENCY);
                boolean hasTroughPutImpact = existingImpacts.contains(THROUGHPUT);

                List<String> impacts = new ArrayList<>();
                if (currentDisruptionImpactPerformance || hasPerformanceImpact) {
                    impacts.add(PERFORMANCE);
                }
                if (currentDisruptionImpactLatency || hasLatencyImpact) {
                    impacts.add(LATENCY);
                }
                if (currentDisruptionImpactTroughPut || hasTroughPutImpact) {
                    impacts.add(THROUGHPUT);
                }

                String newImpacts = impacts.stream().collect(Collectors.joining(", "));
                serviceDisruption.put(SERVICE_DISRUPTION_IMPACT, newImpacts);
            }
        });
    }

    private void buildUpgradesPayload(JsonObject aggregationPayload, JsonObject context) {
        JsonObject upgrades = this.context.getJsonObject(UPGRADES);
        String kafkaVersion = context.getString(KAFKA_VERSION);
        aggregationPayload.getJsonArray(EVENTS).stream().forEach(eventObject -> {
            JsonObject event = (JsonObject) eventObject;
            JsonObject payload = event.getJsonObject(PAYLOAD);
            String name = payload.getString(PAYLOAD_NAME);
            JsonObject kafkaUpgrade;
            if (!upgrades.containsKey(name)) {
                kafkaUpgrade = new JsonObject();
                kafkaUpgrade.put(PAYLOAD_NAME, name);
                kafkaUpgrade.put(KAFKA_VERSION, kafkaVersion);
                upgrades.put(name, kafkaUpgrade);
            } else {
                kafkaUpgrade = upgrades.getJsonObject(name);
                String existingKafkaVersions = kafkaUpgrade.getString(KAFKA_VERSION, "");
                if (!existingKafkaVersions.contains(kafkaVersion)) {
                    kafkaUpgrade.put(kafkaVersion, existingKafkaVersions + ", " + kafkaVersion);
                }
            }
        });
    }
}
