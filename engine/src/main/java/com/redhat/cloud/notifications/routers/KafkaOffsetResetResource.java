package com.redhat.cloud.notifications.routers;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.jboss.resteasy.reactive.RestQuery;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

/**
 * Internal endpoint for resetting Kafka consumer group offsets.
 * Use this to replay messages after an outage where messages were acked but not processed.
 *
 * IMPORTANT: The consumer group must be inactive (paused) before calling these endpoints.
 * Use the Unleash feature flag or KafkaChannelManager to pause the ingress channel first.
 */
@Path(API_INTERNAL + "/kafka-offset")
public class KafkaOffsetResetResource {

    private static final String INGRESS_TOPIC = "platform.notifications.ingress";
    private static final String CONSUMER_GROUP = "integrations";

    @Inject
    AdminClient adminClient;

    /**
     * Reset the consumer group offsets for the ingress topic to a specific timestamp.
     * Messages produced after this timestamp will be re-consumed when the consumer is resumed.
     *
     * @param epochMillis The Unix timestamp in milliseconds to reset to.
     *                    Calculate this as: (outage start time - some buffer) in epoch millis.
     * @return A summary of the reset operation.
     */
    @POST
    @Path("/reset-to-timestamp")
    @Produces(APPLICATION_JSON)
    public OffsetResetResponse resetToTimestamp(@RestQuery Long epochMillis) {
        if (epochMillis == null || epochMillis <= 0) {
            throw new BadRequestException("epochMillis query parameter is required and must be positive");
        }

        Log.infof("Resetting consumer group '%s' offsets for topic '%s' to timestamp %d (%s)",
                CONSUMER_GROUP, INGRESS_TOPIC, epochMillis, Instant.ofEpochMilli(epochMillis));

        try {
            List<TopicPartition> partitions = getTopicPartitions();
            Map<TopicPartition, Long> offsetsAtTimestamp = getOffsetsForTimestamp(partitions, epochMillis);
            Map<TopicPartition, Long> currentOffsets = getCurrentCommittedOffsets(partitions);

            Map<TopicPartition, OffsetAndMetadata> newOffsets = offsetsAtTimestamp.entrySet().stream()
                    .filter(e -> e.getValue() >= 0)
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            e -> new OffsetAndMetadata(e.getValue())
                    ));

            if (newOffsets.isEmpty()) {
                Log.warn("No valid offsets found for the given timestamp. The timestamp may be before the earliest available message.");
                return new OffsetResetResponse(
                        INGRESS_TOPIC,
                        CONSUMER_GROUP,
                        epochMillis,
                        0,
                        "No valid offsets found for timestamp. It may be before earliest available message.",
                        Map.of()
                );
            }

            adminClient.alterConsumerGroupOffsets(CONSUMER_GROUP, newOffsets).all().get();

            Map<String, PartitionOffsetInfo> partitionDetails = new HashMap<>();
            for (Map.Entry<TopicPartition, OffsetAndMetadata> entry : newOffsets.entrySet()) {
                TopicPartition tp = entry.getKey();
                long newOffset = entry.getValue().offset();
                long previousOffset = currentOffsets.getOrDefault(tp, -1L);
                long messagesToReplay = previousOffset > newOffset ? previousOffset - newOffset : 0;

                partitionDetails.put(
                        String.valueOf(tp.partition()),
                        new PartitionOffsetInfo(previousOffset, newOffset, messagesToReplay)
                );
            }

            long totalMessagesToReplay = partitionDetails.values().stream()
                    .mapToLong(p -> p.messagesToReplay)
                    .sum();

            Log.infof("Successfully reset %d partition(s). Approximately %d messages will be replayed.",
                    newOffsets.size(), totalMessagesToReplay);

            return new OffsetResetResponse(
                    INGRESS_TOPIC,
                    CONSUMER_GROUP,
                    epochMillis,
                    totalMessagesToReplay,
                    "Success",
                    partitionDetails
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Offset reset interrupted", e);
        } catch (ExecutionException e) {
            String message = "Failed to reset offsets: " + e.getCause().getMessage();
            Log.error(message, e);
            throw new RuntimeException(message, e.getCause());
        }
    }

    /**
     * Get the current committed offsets and latest offsets for the ingress topic.
     * Use this to inspect the current state before performing a reset.
     */
    @GET
    @Path("/describe")
    @Produces(APPLICATION_JSON)
    public Map<String, PartitionOffsetInfo> describeOffsets() {
        try {
            List<TopicPartition> partitions = getTopicPartitions();
            Map<TopicPartition, Long> currentOffsets = getCurrentCommittedOffsets(partitions);
            Map<TopicPartition, Long> latestOffsets = getLatestOffsets(partitions);

            Map<String, PartitionOffsetInfo> result = new HashMap<>();
            for (TopicPartition tp : partitions) {
                long current = currentOffsets.getOrDefault(tp, -1L);
                long latest = latestOffsets.getOrDefault(tp, -1L);
                long lag = latest > current ? latest - current : 0;
                result.put(String.valueOf(tp.partition()), new PartitionOffsetInfo(current, latest, lag));
            }
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Describe offsets interrupted", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed to describe offsets: " + e.getCause().getMessage(), e.getCause());
        }
    }

    private List<TopicPartition> getTopicPartitions() throws ExecutionException, InterruptedException {
        return adminClient.describeTopics(List.of(INGRESS_TOPIC))
                .allTopicNames().get().get(INGRESS_TOPIC).partitions().stream()
                .map(p -> new TopicPartition(INGRESS_TOPIC, p.partition()))
                .collect(Collectors.toList());
    }

    private Map<TopicPartition, Long> getOffsetsForTimestamp(List<TopicPartition> partitions, long epochMillis)
            throws ExecutionException, InterruptedException {
        Map<TopicPartition, OffsetSpec> timestampSpec = partitions.stream()
                .collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.forTimestamp(epochMillis)));

        ListOffsetsResult result = adminClient.listOffsets(timestampSpec);
        Map<TopicPartition, Long> offsets = new HashMap<>();
        for (TopicPartition tp : partitions) {
            ListOffsetsResult.ListOffsetsResultInfo info = result.partitionResult(tp).get();
            offsets.put(tp, info.offset());
        }
        return offsets;
    }

    private Map<TopicPartition, Long> getCurrentCommittedOffsets(List<TopicPartition> partitions)
            throws ExecutionException, InterruptedException {
        Map<TopicPartition, OffsetAndMetadata> committed =
                adminClient.listConsumerGroupOffsets(CONSUMER_GROUP)
                        .partitionsToOffsetAndMetadata().get();

        Map<TopicPartition, Long> result = new HashMap<>();
        for (TopicPartition tp : partitions) {
            OffsetAndMetadata oam = committed.get(tp);
            result.put(tp, oam != null ? oam.offset() : -1L);
        }
        return result;
    }

    private Map<TopicPartition, Long> getLatestOffsets(List<TopicPartition> partitions)
            throws ExecutionException, InterruptedException {
        Map<TopicPartition, OffsetSpec> latestSpec = partitions.stream()
                .collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.latest()));

        ListOffsetsResult result = adminClient.listOffsets(latestSpec);
        Map<TopicPartition, Long> offsets = new HashMap<>();
        for (TopicPartition tp : partitions) {
            offsets.put(tp, result.partitionResult(tp).get().offset());
        }
        return offsets;
    }

    public record OffsetResetResponse(
            String topic,
            String consumerGroup,
            long resetTimestampEpochMillis,
            long estimatedMessagesToReplay,
            String status,
            Map<String, PartitionOffsetInfo> partitions
    ) { }

    public record PartitionOffsetInfo(
            long previousOffset,
            long newOffset,
            long messagesToReplay
    ) { }
}
