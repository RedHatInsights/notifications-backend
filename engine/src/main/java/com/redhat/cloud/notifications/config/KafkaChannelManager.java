package com.redhat.cloud.notifications.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.getunleash.Unleash;
import io.getunleash.Variant;
import io.getunleash.event.UnleashSubscriber;
import io.getunleash.repository.FeatureToggleResponse;
import io.getunleash.variant.Payload;
import io.quarkus.logging.Log;
import io.smallrye.reactive.messaging.ChannelRegistry;
import io.smallrye.reactive.messaging.PausableChannel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

import static io.getunleash.repository.FeatureToggleResponse.Status.CHANGED;
import static java.lang.Boolean.TRUE;

@ApplicationScoped
public class KafkaChannelManager implements UnleashSubscriber {

    private static final String UNLEASH_TOGGLE_NAME = "notifications.kafka-channels";

    @ConfigProperty(name = "host-name", defaultValue = "localhost")
    String hostName;

    @Inject
    Unleash unleash;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    ChannelRegistry channelRegistry;

    @Override
    public void togglesFetched(FeatureToggleResponse toggleResponse) {
        if (toggleResponse.getStatus() == CHANGED) {
            KafkaChannelConfig[] kafkaChannelConfigs = getKafkaChannelConfigs();
            for (KafkaChannelConfig kafkaChannelConfig : kafkaChannelConfigs) {
                try {
                    if (shouldThisHostBeUpdated(kafkaChannelConfig)) {
                        if (TRUE.equals(kafkaChannelConfig.paused)) {
                            pause(kafkaChannelConfig.channel);
                        } else {
                            resume(kafkaChannelConfig.channel);
                        }
                    }
                } catch (Exception e) {
                    Log.error("Could not pause or resume a channel", e);
                }
            }
        }
    }

    private KafkaChannelConfig[] getKafkaChannelConfigs() {
        Variant variant = unleash.getVariant(UNLEASH_TOGGLE_NAME);
        if (variant.isEnabled()) {
            Optional<Payload> payload = variant.getPayload();
            if (payload.isEmpty()) {
                Log.warn("Variant ignored because of an empty payload");
            } else if (!payload.get().getType().equals("json")) {
                Log.warnf("Variant ignored because of a wrong payload type [expected=json, actual=%s]", payload.get().getType());
            } else if (payload.get().getValue() == null) {
                Log.warn("Variant ignored because of a null payload value");
            } else {
                try {
                    return objectMapper.readValue(payload.get().getValue(), KafkaChannelConfig[].class);
                } catch (JsonProcessingException e) {
                    Log.error("Variant payload deserialization failed", e);
                }
            }
        }
        return new KafkaChannelConfig[0];
    }

    private boolean shouldThisHostBeUpdated(KafkaChannelConfig kafkaChannelConfig) {
        if (kafkaChannelConfig.hostName == null) {
            return true;
        }
        if (kafkaChannelConfig.hostName.endsWith("*")) {
            return hostName.startsWith(kafkaChannelConfig.hostName.substring(0, kafkaChannelConfig.hostName.length() - 1));
        } else {
            return hostName.equals(kafkaChannelConfig.hostName);
        }
    }

    private void pause(String channel) {
        PausableChannel pausableChannel = getPausableChannel(channel);
        if (!pausableChannel.isPaused()) {
            pausableChannel.pause();
            Log.infof("Paused channel %s", channel);
        }
    }

    private void resume(String channel) {
        PausableChannel pausableChannel = getPausableChannel(channel);
        if (pausableChannel.isPaused()) {
            pausableChannel.resume();
            Log.infof("Resumed channel %s", channel);
        }
    }

    private PausableChannel getPausableChannel(String channel) {
        PausableChannel pausableChannel = channelRegistry.getPausable(channel);
        if (pausableChannel == null) {
            throw new RuntimeException("Channel not found or not marked as pausable in application.properties: " +  channel);
        } else {
            return pausableChannel;
        }
    }
}
