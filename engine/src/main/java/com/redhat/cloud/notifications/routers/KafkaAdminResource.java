package com.redhat.cloud.notifications.routers;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.GroupListing;
import org.apache.kafka.clients.admin.ListGroupsResult;
import org.eclipse.microprofile.config.Config;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;

@Path(API_INTERNAL + "/kafka-admin")
public class KafkaAdminResource {

    @Inject
    Config config;

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
                Log.infof("Security.protocol config found and applied");
            });

        config.getOptionalValue("kafka.sasl.mechanism", String.class)
            .ifPresent(val -> {
                adminConfig.put("sasl.mechanism", val);
                Log.infof("Sasl.mechanism config found and applied", val);
            });

        config.getOptionalValue("kafka.sasl.jaas.config", String.class)
            .ifPresent(val -> {
                adminConfig.put("sasl.jaas.config", val);
                Log.info("SASL JAAS config found and applied");
            });

        config.getOptionalValue("kafka.ssl.truststore.location", String.class)
            .ifPresent(val -> {
                adminConfig.put("ssl.truststore.location", val);
                Log.infof("Ssl.truststore.location config found and applied", val);
            });

        config.getOptionalValue("kafka.ssl.truststore.type", String.class)
            .ifPresent(val -> {
                adminConfig.put("ssl.truststore.type", val);
                Log.infof("Ssl.truststore.type config found and applied", val);
            });

        return AdminClient.create(adminConfig);
    }

    @GET
    @Path("/consumer-groups")
    public Response listConsumerGroups() {
        Log.info("Listing all consumer groups");

        try (AdminClient adminClient = createAdminClient()) {
            // Use listGroups without filter (will return all groups)
            ListGroupsResult result = adminClient.listGroups();

            String groups = result.all().get().stream()
                .map(GroupListing::groupId)
                .collect(Collectors.joining(", "));

            Log.infof("Found consumer groups: %s", groups);
            return Response.ok(groups).build();

        } catch (Exception e) {
            Log.errorf(e, "Failed to list consumer groups");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("Failed to list consumer groups: " + e.getMessage())
                .build();
        }
    }
}
