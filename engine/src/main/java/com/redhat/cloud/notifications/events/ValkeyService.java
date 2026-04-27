package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.config.EngineConfig;
import io.quarkus.logging.Log;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.redis.client.Redis;
import io.vertx.mutiny.redis.client.RedisAPI;
import io.vertx.mutiny.redis.client.Response;
import io.vertx.redis.client.RedisOptions;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Stores and retrieves data from remote cache (i.e. Valkey). */
@ApplicationScoped
public class ValkeyService {

    private static final String EVENT_DEDUPLICATION_KEY = "engine:event-deduplication";

    @ConfigProperty(name = "valkey-service.ttl", defaultValue = "PT24H")
    Duration ttl;

    @ConfigProperty(name = "quarkus.redis.hosts", defaultValue = "")
    Optional<String> valkeyHost;

    @ConfigProperty(name = "quarkus.redis.password", defaultValue = "")
    Optional<String> valkeyPassword;

    @Inject
    EngineConfig config;

    @Inject
    Vertx vertx;

    /** The underlying client connecting to Valkey. */
    private Redis valkeyClient;

    /** Implementation of the Redis/Valkey API, using {@link #valkeyClient} */
    private RedisAPI valkey;

    @PostConstruct
    void initialize() {
        if (config.isInMemoryDbEnabled()) {
            if (valkeyHost.isEmpty() || valkeyHost.get().isEmpty()) {
                throw new IllegalStateException("In-memory DB enabled, but Valkey connection string was not provided");
            } else {
                RedisOptions valkeyOptions = new RedisOptions().setConnectionString(valkeyHost.get().replace("valkey://", "redis://"));
                valkeyPassword.ifPresent(valkeyOptions::setPassword);

                this.valkeyClient = Redis.createClient(vertx, valkeyOptions);
                this.valkey = RedisAPI.api(this.valkeyClient);
            }
        }
    }

    public static String formatDeduplicationKey(UUID eventTypeId, String deduplicationKey) {
        return String.format("%s:%s:%s", EVENT_DEDUPLICATION_KEY, eventTypeId, deduplicationKey);
    }

    public String runHealthCheck() throws Exception {
        if (config.isInMemoryDbEnabled()) {
            return valkey.ping(List.of()).await().atMost(Duration.ofSeconds(10)).toString();
        } else {
            return "In-memory DB is disabled";
        }
    }

    /**
     * Verifies that the event has not been previously processed. The format of saved keys is
     * {@code engine:event-deduplication:<event_type>:<deduplication_key>}.
     *
     * @see com.redhat.cloud.notifications.events.deduplication.EventDeduplicator EventDeduplicator
     */
    public boolean isNewEvent(UUID eventTypeId, String deduplicationKey, LocalDateTime deleteAfter) {
        String key = formatDeduplicationKey(eventTypeId, deduplicationKey);
        String deleteAfterIso = deleteAfter.format(DateTimeFormatter.ISO_DATE_TIME);
        boolean isNew;

        Response valkeyResp = valkey.setAndAwait(List.of(
                key,
                deleteAfterIso,
                "NX",
                "EXAT",
                String.valueOf(deleteAfter.toEpochSecond(ZoneOffset.UTC))
        ));

        if (valkeyResp == null) {
            isNew = false;
        } else {
            try {
                isNew = valkeyResp.toString().equals("OK");
            } catch (Exception ignored) {
                // Invalid response could not be mapped to string. Assume event is new
                // dedup key may include private information, so other fields are used
                Log.warnf("unable to check for duplicate event in Valkey [event_type_id=%s, delete_after=%s, deduplication_key=%s]",
                        eventTypeId, deleteAfterIso, deduplicationKey);
                isNew = true;
            }
        }

        return isNew;
    }

    /** This method should only be called to remove a key that may have been inserted by {@link #isNewEvent(UUID, String, LocalDateTime)} */
    public boolean removeEventFromDeduplication(UUID eventTypeId, String deduplicationKey) {
        try {
            return valkey.delAndAwait(List.of(formatDeduplicationKey(eventTypeId, deduplicationKey))).toBoolean();
        } catch (Exception ignored) {
            Log.warnf(
                    "Failed to remove duplicate event from Valkey during rollback [event_type_id=%s, deduplication_key=%s]",
                    eventTypeId, deduplicationKey);
            return false;
        }
    }
}
