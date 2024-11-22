package com.redhat.cloud.notifications.config;

import io.quarkus.redis.client.RedisHostsProvider;
import io.smallrye.common.annotation.Identifier;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

/**
 * Modifies the Redis host URI returned by the <a href="https://github.com/RedHatInsights/clowder-quarkus-config-source">clowder-quarkus-config-source</a>
 * extension to use the TLS variant of the URI scheme (<code>rediss://</code>).
 * <br>
 * TODO: determine if this will break without a default
 */
@ApplicationScoped
@Identifier("tls-redis-hosts-provider")
public class TlsRedisHostsProvider implements RedisHostsProvider {

    @ConfigProperty(name = "quarkus.redis.hosts")
    String redisHosts;

    @Override
    public Set<URI> getHosts() {
        URI redisHostsUri = URI.create(redisHosts);
        try {
            return Set.of(new URI("rediss", redisHostsUri.getSchemeSpecificPart(), ""));
        } catch (URISyntaxException e) {
            throw new RuntimeException("Invalid Redis host URI provided", e);
        }
    }
}
