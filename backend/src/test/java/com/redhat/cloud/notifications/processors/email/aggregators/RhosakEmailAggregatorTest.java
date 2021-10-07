package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.models.EmailAggregation;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.redhat.cloud.notifications.TestHelpers.baseTransformer;
import static org.junit.jupiter.api.Assertions.*;

class RhosakEmailAggregatorTest {
    public static final String APPLICATION_SERVICES = "application-services";
    public static final String RHOSAK = "rhosak";
    public static final String ACCOUNT_ID = "1787";
    public static final String DISRUPTION = "disruption";
    public static final String SCHEDULED_UPGRADE = "scheduled-upgrade";
    public static final String UPGRADES = "upgrades";
    public static final String DISRUPTIONS = "disruptions";
    RhosakEmailAggregator aggregator;
    JsonObject context;
    JsonObject upgrades;
    JsonObject disruptions;

    @BeforeEach
    public void setup() {
        aggregator = new RhosakEmailAggregator();
        context = aggregator.context;
        upgrades = context.getJsonObject(UPGRADES);
        disruptions = context.getJsonObject(DISRUPTIONS);
    }

    @Test
    void emptyAggregatorHasNoAccountId() {
        assertNull(aggregator.getAccountId(), "Empty aggregator has no accountId");
    }

    @Test
    void emptyAggregatorHasEmptyUpgradesAndEmptyDisruptions() {
        assertNotNull(upgrades, "Empty aggregator should have upgrades body initialized");
        assertEquals(0, upgrades.size(), "Empty aggregator should have empty upgrades body");
        assertNotNull(disruptions, "Empty aggregator should have disruptions body initialized");
        assertEquals(0, disruptions.size(), "Empty aggregator should have empty disruption body");
    }

    @Test
    void upgradesAggregationTests() {
        String kafkaName = "my-kafka";
        String firstKafkaVersion = "2.8.0";
        String kafkaVersion = "kafka_version";

        // test first aggregation
        EmailAggregation aggregation = createUpgradeEmailAggregation(kafkaName, firstKafkaVersion);
        aggregator.processEmailAggregation(aggregation);

        assertEquals(1, upgrades.size(), "aggregator should have one disruption body");
        JsonObject entry = upgrades.getJsonObject(kafkaName);
        assertNotNull(entry);
        assertEquals(firstKafkaVersion, entry.getString(kafkaVersion));

        // test second aggregation
        String secondKafkaVersion = "3.0.0";
        aggregation = createUpgradeEmailAggregation(kafkaName, secondKafkaVersion);
        aggregator.processEmailAggregation(aggregation);

        assertEquals(1, upgrades.size(), "aggregator should still have one disruption body");
        entry = upgrades.getJsonObject(kafkaName);
        assertEquals(firstKafkaVersion + ", " + secondKafkaVersion, entry.getString(kafkaVersion));

        // same upgrade happening again
        aggregation = createUpgradeEmailAggregation(kafkaName, "2.8.0 ");
        aggregator.processEmailAggregation(aggregation);

        assertEquals(1, upgrades.size(), "aggregator should still have one disruption body");
        entry = upgrades.getJsonObject(kafkaName);
        assertEquals(firstKafkaVersion + ", " + secondKafkaVersion, entry.getString(kafkaVersion));

        // test fifth aggregation with another kafka
        String kafkaName1 = "another-kafka-name";
        aggregation = createUpgradeEmailAggregation(kafkaName1, firstKafkaVersion);
        aggregator.processEmailAggregation(aggregation);

        assertEquals(2, upgrades.size(), "aggregator should still have two disruption bodies");
        entry = upgrades.getJsonObject(kafkaName1);
        assertEquals(firstKafkaVersion, entry.getString(kafkaVersion));

        // there has been no upgrades aggregation
        assertEquals(0, disruptions.size(), "scheduled upgrades only aggregator should have empty disruption body");
    }

    @Test
    void serviceDisruptionAggregationTests() {
        String kafkaName = "my-kafka";
        String perf = "performance";
        String impactedArea = "impacted_area";

        // test first aggregation
        EmailAggregation aggregation = createDisruptionEmailAggregation(kafkaName, perf);
        aggregator.processEmailAggregation(aggregation);

        assertEquals(1, disruptions.size(), "aggregator should have one disruption body");
        JsonObject entry = disruptions.getJsonObject(kafkaName);
        assertNotNull(entry);
        assertEquals(perf, entry.getString(impactedArea));

        // test second aggregation
        String throughput = "throughput";
        aggregation = createDisruptionEmailAggregation(kafkaName, throughput);
        aggregator.processEmailAggregation(aggregation);

        assertEquals(1, disruptions.size(), "aggregator should still have one disruption body");
        entry = disruptions.getJsonObject(kafkaName);
        assertEquals(perf + ", " + throughput, entry.getString(impactedArea));

        // test third aggregation
        String latency = "latency";
        aggregation = createDisruptionEmailAggregation(kafkaName, latency);
        aggregator.processEmailAggregation(aggregation);

        assertEquals(1, disruptions.size(), "aggregator should still have one disruption body");
        entry = disruptions.getJsonObject(kafkaName);
        assertEquals(perf + ", " + latency + ", " + throughput, entry.getString(impactedArea));

        // test fourth aggregation
        String impactedArea1 = "latency, performance, throughput";
        aggregation = createDisruptionEmailAggregation(kafkaName, impactedArea1);
        aggregator.processEmailAggregation(aggregation);

        assertEquals(1, disruptions.size(), "aggregator should still have one disruption body");
        entry = disruptions.getJsonObject(kafkaName);
        assertEquals(perf + ", " + latency + ", " + throughput, entry.getString(impactedArea));

        // test fifth aggregation with another kafka
        String kafkaName1 = "another-kafka-name";
        aggregation = createDisruptionEmailAggregation(kafkaName1, impactedArea1);
        aggregator.processEmailAggregation(aggregation);

        assertEquals(2, disruptions.size(), "aggregator should still have two disruption bodies");
        entry = disruptions.getJsonObject(kafkaName1);
        assertEquals(impactedArea1, entry.getString(impactedArea));

        // there has been no upgrades aggregation
        assertEquals(0, upgrades.size(), "service disruption only aggregator should have empty upgrades body");
    }

    @Test
    void allAggregationTests() {
        String kafkaName = "my-kafka";

        // disruption aggregation
        EmailAggregation aggregation = createDisruptionEmailAggregation(kafkaName, "performance");
        aggregator.processEmailAggregation(aggregation);

        // check upgrades aggregation
        aggregation = createUpgradeEmailAggregation(kafkaName, "2.8.0");
        aggregator.processEmailAggregation(aggregation);

        assertEquals(1, disruptions.size(), "aggregator should have content in disruption body");
        assertEquals(1, upgrades.size(), "aggregator should have content in upgrades body");
    }

    private static EmailAggregation createDisruptionEmailAggregation(String kafkaName, String impactedArea) {
        EmailAggregation aggregation = new EmailAggregation();
        aggregation.setBundleName(APPLICATION_SERVICES);
        aggregation.setApplicationName(RHOSAK);
        aggregation.setAccountId(ACCOUNT_ID);

        Action emailActionMessage = new Action();
        emailActionMessage.setBundle(APPLICATION_SERVICES);
        emailActionMessage.setApplication(RHOSAK);
        emailActionMessage.setTimestamp(LocalDateTime.now());
        emailActionMessage.setEventType(DISRUPTION);

        emailActionMessage.setContext(Map.of(
                "impacted_area", impactedArea
        ));
        emailActionMessage.setEvents(List.of(
                Event
                        .newBuilder()
                        .setMetadataBuilder(Metadata.newBuilder())
                        .setPayload(Map.of(
                                "id", kafkaName,
                                "name", kafkaName
                        ))
                        .build()
        ));

        emailActionMessage.setAccountId(ACCOUNT_ID);

        JsonObject payload = baseTransformer.transform(emailActionMessage).await().indefinitely();
        aggregation.setPayload(payload);

        return aggregation;
    }

    private static EmailAggregation createUpgradeEmailAggregation(String kafkaName, String kafkaVersion) {
        EmailAggregation aggregation = new EmailAggregation();
        aggregation.setBundleName(APPLICATION_SERVICES);
        aggregation.setApplicationName(RHOSAK);
        aggregation.setAccountId(ACCOUNT_ID);

        Action emailActionMessage = new Action();
        emailActionMessage.setBundle(APPLICATION_SERVICES);
        emailActionMessage.setApplication(RHOSAK);
        emailActionMessage.setTimestamp(LocalDateTime.now());
        emailActionMessage.setEventType(SCHEDULED_UPGRADE);

        emailActionMessage.setContext(Map.of(
                "kafka_version", kafkaVersion
        ));
        emailActionMessage.setEvents(List.of(
                Event
                        .newBuilder()
                        .setMetadataBuilder(Metadata.newBuilder())
                        .setPayload(Map.of(
                                "id", kafkaName,
                                "name", kafkaName
                        ))
                        .build()
        ));

        emailActionMessage.setAccountId(ACCOUNT_ID);

        JsonObject payload = baseTransformer.transform(emailActionMessage).await().indefinitely();
        aggregation.setPayload(payload);

        return aggregation;
    }
}
