package com.redhat.cloud.notifications.config;

import io.smallrye.config.ConfigSourceInterceptor;
import io.smallrye.config.ConfigSourceInterceptorContext;
import io.smallrye.config.ConfigValue;
import io.smallrye.config.Priorities;
import io.smallrye.config.SecretKeys;
import jakarta.annotation.Priority;

/**
 * <p>A configuration interceptor which helps overriding the resolved
 * configuration parameters for Kessel's inventory and relations APIs.</p>
 *
 * <p>The <a href="https://github.com/RedHatInsights/clowder-quarkus-config-source">clowder-quarkus-config-source</a>
 * extension is very helpful to load the Clowder endpoints because it gets both
 * the {@code hostname} and the {@code port} of the endpoints and returns them
 * in the following format: {@code ${SCHEME}${HOST}${PORT}}, with the {@code
 * scheme} being either {@code http} or {@code https}.</p>
 *
 * <p>The issue with the Kessel's properties is that the gRPC clients are
 * expecting just the hostname and the port without any protocol scheme
 * prepended, so when using the above extension the URLs always get prefixed
 * with the HTTP protocol's schemes, and therefore the connections cannot be
 * established with the gRPC servers.</p>
 *
 * <p>This class intercepts the resolved values for the specific properties of
 * the clients and strips the scheme part, so that the Kessel clients are built
 * with the proper URLs instead.</p>
 *
 * <p>The implementation was done following the <a href="https://quarkus.io/guides/config-extending-support#config-interceptors">
 * Extending Configuration Support Quarkus tutorial</a>.</p>
 */
@Priority(Priorities.APPLICATION)
public class KesselConfigInterceptor implements ConfigSourceInterceptor {
    /**
     * Overrides the {@code inventory-api.target-url}'s and
     * {@code relations-api.target-url}'s URL by removing the "http" or "https"
     * schemes.
     * @param configSourceInterceptorContext the configuration source's
     *                                       interceptor's context.
     * @param name the name of the configuration property we are loading.
     * @return the resolved configuration properties without the "http" or
     * "https" schemes.
     */
    @Override
    public ConfigValue getValue(final ConfigSourceInterceptorContext configSourceInterceptorContext, final String name) {
        final ConfigValue configValue = SecretKeys.doLocked(() -> configSourceInterceptorContext.proceed(name));

        if ((configValue != null) && (name.equals("inventory-api.target-url") || name.equals("relations-api.target-url"))) {
            final String kesselUrl = configValue.getValue();

            if (kesselUrl.startsWith("http://")) {
                return configValue.withValue(kesselUrl.replace("http://", ""));
            } else {
                return configValue.withValue(kesselUrl.replace("https://", ""));
            }
        }

        return configValue;
    }
}
