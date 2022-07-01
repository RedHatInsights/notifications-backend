package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.templates.Rhosak.Templates;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.TestHelpers.baseTransformer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class RhosakEmailAggregatorTest {
    public static final String APPLICATION_SERVICES = "application-services";
    public static final String RHOSAK = "rhosak";
    public static final String ACCOUNT_ID = "1787";
    public static final String DISRUPTION = "disruption";
    public static final String SCHEDULED_UPGRADE = "scheduled-upgrade";
    public static final String UPGRADES = "upgrades";
    public static final String DISRUPTIONS = "disruptions";
    public static final LocalDateTime NOW = LocalDateTime.now(ZoneOffset.UTC);
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
    void emptyAggregatorHasNoAccountIdOrOrgId() {
        assertNull(aggregator.getAccountId(), "Empty aggregator has no accountId");
        assertNull(aggregator.getOrgId(), "Empty aggregator has no orgId");
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
        String upgrade_time = "upgrade_time";

        // test first aggregation
        EmailAggregation aggregation = createUpgradeEmailAggregation(kafkaName, firstKafkaVersion);
        aggregator.processEmailAggregation(aggregation);

        assertEquals(1, upgrades.size(), "aggregator should have one disruption body");
        JsonObject entry = upgrades.getJsonObject(kafkaName);
        assertNotNull(entry);
        assertEquals(firstKafkaVersion, entry.getString(kafkaVersion));
        assertNotNull(entry.getString(upgrade_time));

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
        assertNotNull(entry.getString("start_time"));

        // test third aggregation
        String latency = "latency";
        aggregation = createDisruptionEmailAggregation(kafkaName, latency);
        aggregator.processEmailAggregation(aggregation);

        assertEquals(1, disruptions.size(), "aggregator should still have one disruption body");
        entry = disruptions.getJsonObject(kafkaName);
        assertEquals(perf + ", " + latency + ", " + throughput, entry.getString(impactedArea));

        // test fourth aggregation
        String impactedArea1 = "latency, performance, throughput, availability";
        aggregation = createDisruptionEmailAggregation(kafkaName, impactedArea1);
        aggregator.processEmailAggregation(aggregation);

        assertEquals(1, disruptions.size(), "aggregator should still have one disruption body");
        entry = disruptions.getJsonObject(kafkaName);
        assertEquals(perf + ", " + latency + ", " + throughput + ", availability", entry.getString(impactedArea));

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

        // test template render
        TemplateInstance dailyBodyTemplateInstance = Templates.dailyRhosakEmailsBody();
        TemplateInstance dailyTittleTemplateInstance = Templates.dailyRhosakEmailsTitle();

        Action emailActionMessage = new Action();
        aggregator.setStartTime(LocalDateTime.now());
        Context.ContextBuilder contextBuilder = new Context.ContextBuilder();
        aggregator.getContext().forEach(contextBuilder::withAdditionalProperty);

        emailActionMessage.setContext(contextBuilder.build());
        String title = dailyTittleTemplateInstance.data("action", emailActionMessage).render();
        assertTrue(title.contains("Red Hat OpenShift Streams for Apache Kafka Daily Report"), "Title must contain RHOSAK related digest info");
        String body = dailyBodyTemplateInstance.data("action", emailActionMessage).data("user", Map.of("firstName", "machi1990", "lastName", "Last Name")).render();
        assertTrue(body.contains("The following table summarizes the OpenShift Streams instances affected by unexpected disruptions of the OpenShift Streams service."), "Body must contain service disruption summary");
        assertTrue(body.contains("The following table summarizes Kafka upgrade activity for your OpenShift Streams instances."), "Body must contain upgrades summary");
        assertTrue(body.contains("Hello machi1990."), "Body must contain greeting message");
        assertTrue(body.contains("This is the daily report for your OpenShift Streams instances"), "Body must contain greeting message");
    }

    private static EmailAggregation createDisruptionEmailAggregation(String kafkaName, String impactedArea) {
        EmailAggregation aggregation = new EmailAggregation();
        aggregation.setBundleName(APPLICATION_SERVICES);
        aggregation.setApplicationName(RHOSAK);
        aggregation.setAccountId(ACCOUNT_ID);
        aggregation.setOrgId(DEFAULT_ORG_ID);

        Action emailActionMessage = new Action();
        emailActionMessage.setBundle(APPLICATION_SERVICES);
        emailActionMessage.setApplication(RHOSAK);
        emailActionMessage.setTimestamp(NOW);
        emailActionMessage.setEventType(DISRUPTION);

        emailActionMessage.setContext(
                new Context.ContextBuilder()
                        .withAdditionalProperty("impacted_area", impactedArea)
                        .build()
        );
        emailActionMessage.setEvents(List.of(
                new Event.EventBuilder()
                        .withMetadata(new Metadata.MetadataBuilder().build())
                        .withPayload(
                                new Payload.PayloadBuilder()
                                        .withAdditionalProperty("id", kafkaName)
                                        .withAdditionalProperty("name", kafkaName)
                                        .build()
                        )
                        .build()
        ));

        emailActionMessage.setAccountId(ACCOUNT_ID);
        emailActionMessage.setOrgId(DEFAULT_ORG_ID);

        JsonObject payload = baseTransformer.transform(emailActionMessage);
        aggregation.setPayload(payload);

        return aggregation;
    }

    private static EmailAggregation createUpgradeEmailAggregation(String kafkaName, String kafkaVersion) {
        EmailAggregation aggregation = new EmailAggregation();
        aggregation.setBundleName(APPLICATION_SERVICES);
        aggregation.setApplicationName(RHOSAK);
        aggregation.setAccountId(ACCOUNT_ID);
        aggregation.setOrgId(DEFAULT_ORG_ID);

        Action emailActionMessage = new Action();
        emailActionMessage.setBundle(APPLICATION_SERVICES);
        emailActionMessage.setApplication(RHOSAK);
        emailActionMessage.setTimestamp(NOW);
        emailActionMessage.setEventType(SCHEDULED_UPGRADE);

        emailActionMessage.setContext(
                new Context.ContextBuilder()
                        .withAdditionalProperty("kafka_version", kafkaVersion)
                        .withAdditionalProperty("upgrade_time", LocalDateTime.now().toString())
                        .build()
        );
        emailActionMessage.setEvents(List.of(
                new Event.EventBuilder()
                        .withMetadata(new Metadata.MetadataBuilder().build())
                        .withPayload(
                                new Payload.PayloadBuilder()
                                        .withAdditionalProperty("id", kafkaName)
                                        .withAdditionalProperty("name", kafkaName)
                                        .build()
                        )
                        .build()
        ));

        emailActionMessage.setAccountId(ACCOUNT_ID);
        emailActionMessage.setOrgId(DEFAULT_ORG_ID);

        JsonObject payload = baseTransformer.transform(emailActionMessage);
        aggregation.setPayload(payload);

        return aggregation;
    }
}
