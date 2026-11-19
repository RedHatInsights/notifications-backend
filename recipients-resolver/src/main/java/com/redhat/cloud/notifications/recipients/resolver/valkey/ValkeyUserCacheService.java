package com.redhat.cloud.notifications.recipients.resolver.valkey;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.recipients.config.RecipientsResolverConfig;
import com.redhat.cloud.notifications.recipients.model.User;
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
import java.util.List;
import java.util.Optional;

/** Caches IT user data in Valkey for cross-pod sharing. */
@ApplicationScoped
public class ValkeyUserCacheService {

    private static final String CACHE_KEY_PREFIX = "recipients:users";
    private static final TypeReference<List<User>> USER_LIST_TYPE = new TypeReference<>() { };

    @ConfigProperty(name = "quarkus.redis.hosts", defaultValue = "")
    Optional<String> valkeyHost;

    @ConfigProperty(name = "quarkus.redis.password", defaultValue = "")
    Optional<String> valkeyPassword;

    @Inject
    RecipientsResolverConfig config;

    @Inject
    Vertx vertx;

    @Inject
    ObjectMapper objectMapper;

    private Redis valkeyClient;
    private RedisAPI valkey;

    @PostConstruct
    void initialize() {
        if (config.isInMemoryDbEnabled()) {
            if (valkeyHost.isEmpty() || valkeyHost.get().isEmpty()) {
                throw new IllegalStateException("In-memory DB enabled, but Valkey connection string was not provided");
            }
            String connectionString = valkeyHost.get()
                    .replace("valkey://", "redis://")
                    .replace("valkeys://", "rediss://");
            RedisOptions valkeyOptions = new RedisOptions().setConnectionString(connectionString);
            if (connectionString.startsWith("rediss://")) {
                valkeyOptions.getNetClientOptions().setHostnameVerificationAlgorithm("HTTPS");
            }
            valkeyPassword.ifPresent(valkeyOptions::setPassword);

            this.valkeyClient = Redis.createClient(vertx, valkeyOptions);
            this.valkey = RedisAPI.api(this.valkeyClient);
            Log.info("Valkey user cache service initialized");
        } else {
            Log.info("Valkey user cache service disabled (in-memory-db.enabled=false)");
        }
    }

    /**
     * Retrieves cached users from Valkey.
     *
     * @return cached user list, or empty if cache miss or Valkey disabled
     */
    public Optional<List<User>> getCachedUsers(String orgId, boolean adminsOnly) {
        if (!config.isInMemoryDbEnabled()) {
            return Optional.empty();
        }
        try {
            String key = formatKey(orgId, adminsOnly);
            Response response = valkey.getAndAwait(key);
            if (response == null) {
                return Optional.empty();
            }
            String json = response.toString();
            List<User> users = objectMapper.readValue(json, USER_LIST_TYPE);
            Log.debugf("Valkey cache hit for key %s, %d users", key, users.size());
            return Optional.of(users);
        } catch (Exception e) {
            Log.warnf("Failed to read users from Valkey cache: %s", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Stores users in Valkey with the configured TTL.
     */
    public void cacheUsers(String orgId, boolean adminsOnly, List<User> users) {
        if (!config.isInMemoryDbEnabled()) {
            return;
        }
        try {
            String key = formatKey(orgId, adminsOnly);
            String json = objectMapper.writeValueAsString(users);
            long ttlSeconds = config.getValkeyUserCacheTtl().toSeconds();
            valkey.setAndAwait(List.of(key, json, "EX", String.valueOf(ttlSeconds)));
            Log.debugf("Cached %d users in Valkey for key %s (TTL %ds)", (Object) users.size(), key, ttlSeconds);
        } catch (JsonProcessingException e) {
            Log.warnf("Failed to serialize users for Valkey cache: %s", e.getMessage());
        } catch (Exception e) {
            Log.warnf("Failed to write users to Valkey cache: %s", e.getMessage());
        }
    }

    public String runHealthCheck() throws Exception {
        if (config.isInMemoryDbEnabled()) {
            return valkey.ping(List.of()).await().atMost(Duration.ofSeconds(10)).toString();
        }
        return "In-memory DB is disabled";
    }

    static String formatKey(String orgId, boolean adminsOnly) {
        return String.format("%s:%s:%s", CACHE_KEY_PREFIX, orgId, adminsOnly);
    }
}
