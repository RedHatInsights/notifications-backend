package com.redhat.cloud.notifications.routers;

import io.quarkus.logging.Log;
import io.smallrye.reactive.messaging.kafka.KafkaClientService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListGroupsResult;
import org.apache.kafka.common.PartitionInfo;
import org.eclipse.microprofile.config.Config;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.events.EventConsumer.INGRESS_REPLAY_CHANNEL;

@Path(API_INTERNAL + "/kafka-admin")
public class KafkaAdminResource {

    @Inject
    Config config;

    @Inject
    KafkaClientService kafkaClientService;

    private AdminClient createAdminClient() {
        Map<String, Object> adminConfig = new HashMap<>();

        // Get bootstrap servers from Clowder config
        String bootstrapServers = config.getValue("kafka.bootstrap.servers", String.class);
        adminConfig.put("bootstrap.servers", bootstrapServers);

        Log.infof("Using Kafka bootstrap servers: %s", bootstrapServers);

        // Check if we have security config from Clowder
        // These will be populated by Clowder if MSK uses SASL/SSL
        config.getOptionalValue("kafka.security.protocol", String.class)
            .ifPresent(val -> {
                adminConfig.put("security.protocol", val);
                Log.info("Security.protocol config found and applied");
            });

        config.getOptionalValue("kafka.sasl.mechanism", String.class)
            .ifPresent(val -> {
                adminConfig.put("sasl.mechanism", val);
                Log.info("Sasl.mechanism config found and applied");
            });

        config.getOptionalValue("kafka.sasl.jaas.config", String.class)
            .ifPresent(val -> {
                adminConfig.put("sasl.jaas.config", val);
                Log.info("SASL JAAS config found and applied");
            });

        config.getOptionalValue("kafka.ssl.truststore.location", String.class)
            .ifPresent(val -> {
                adminConfig.put("ssl.truststore.location", val);
                Log.info("Ssl.truststore.location config found and applied");
            });

        config.getOptionalValue("kafka.ssl.truststore.type", String.class)
            .ifPresent(val -> {
                adminConfig.put("ssl.truststore.type", val);
                Log.info("Ssl.truststore.type config found and applied");
            });

        return AdminClient.create(adminConfig);
    }

    @GET
    @Path("/consumer-groups")
    public Response listConsumerGroups() throws ExecutionException, InterruptedException {
        Log.info("Listing all consumer groups");

        AdminClient adminClient = createAdminClient();
        // Use listGroups without filter (will return all groups)
        ListGroupsResult result = adminClient.listGroups();

        long groups = result.all().get().size();

        Log.infof("Found consumer groups: %d", groups);
        return Response.ok().build();
    }

    @GET
    @Path("/listTopics")
    public Response listTopics() {
        var consumer = kafkaClientService.getConsumer(INGRESS_REPLAY_CHANNEL);

        // Most operations must run on the polling thread
        consumer.runOnPollingThread(kafkaConsumer -> {
            Map<String, List<PartitionInfo>> topics = kafkaConsumer.listTopics();
            Log.infof("Found %d topics", topics.size());
        }).await().atMost(Duration.ofSeconds(30));
        return Response.ok().build();
    }
}
