package com.redhat.cloud.notifications.config;

import io.quarkus.logging.Log;
import io.smallrye.config.ConfigSourceInterceptor;
import io.smallrye.config.ConfigSourceInterceptorContext;
import io.smallrye.config.ConfigValue;
import io.smallrye.config.Priorities;
import io.smallrye.config.SecretKeys;
import jakarta.annotation.Priority;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * <p>A configuration interceptor which modifies the URL scheme to enable TLS connectivity for the
 * Redis cache backend.</p>
 *
 * <p>The implementation was done following the <a href="https://quarkus.io/guides/config-extending-support#config-interceptors">
 * Extending Configuration Support Quarkus tutorial</a>.</p>
 */
@Priority(Priorities.APPLICATION)
public class RedisHostConfigInterceptor implements ConfigSourceInterceptor {
    /**
     * Overrides the {@code quarkus.redis.hosts} URL by replacing the scheme
     * with "rediss".
     * @param configSourceInterceptorContext the configuration source's
     *                                       interceptor's context.
     * @param name the name of the configuration property we are loading.
     * @return the resolved configuration properties with the "https" scheme.
     */
    @Override
    public ConfigValue getValue(final ConfigSourceInterceptorContext configSourceInterceptorContext, final String name) {
        final ConfigValue configValue = SecretKeys.doLocked(() -> configSourceInterceptorContext.proceed(name));

        if (configValue != null && name.equals("quarkus.redis.hosts")) {
            final String redisHost = configValue.getValue();
            try {
                URI uri = new URI(redisHost);
                final URI newRedisHost = new URI("rediss", uri.getSchemeSpecificPart(), "");

                Log.debugf("Redis host URI for property \"%s\" changed from \"%s\" to \"%s\"", name, redisHost, newRedisHost);

                return configValue.withValue(newRedisHost.toString());
            } catch (final IllegalArgumentException | URISyntaxException e) {
                Log.debugf(e, "Unable to create a URI from the configuration property \"%s\"'s value \"%s\"", name, redisHost);

                return configValue;
            }
        }

        return configValue;
    }
}
