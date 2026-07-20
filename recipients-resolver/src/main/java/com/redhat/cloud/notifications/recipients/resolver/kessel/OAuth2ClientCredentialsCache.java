package com.redhat.cloud.notifications.recipients.resolver.kessel;

import com.redhat.cloud.notifications.recipients.config.RecipientsResolverConfig;
import io.quarkus.cache.CacheInvalidateAll;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.project_kessel.api.auth.ClientConfigAuth;
import org.project_kessel.api.auth.OAuth2ClientCredentials;
import org.project_kessel.api.auth.OIDCDiscovery;
import org.project_kessel.api.auth.OIDCDiscoveryMetadata;

@ApplicationScoped
public class OAuth2ClientCredentialsCache {

    private static final String CACHE_NAME = "kessel-oauth2-client-credentials";

    @Inject
    RecipientsResolverConfig recipientsResolverConfig;

    @CacheResult(cacheName = CACHE_NAME)
    public OAuth2ClientCredentials getCredentials() {

        OIDCDiscoveryMetadata discoveryMetadata = OIDCDiscovery.fetchOIDCDiscovery(recipientsResolverConfig.getKesselClientIssuer().get());
        ClientConfigAuth auth = new ClientConfigAuth(
            recipientsResolverConfig.getKesselClientId().get(),
            recipientsResolverConfig.getKesselClientSecret().get(),
            discoveryMetadata.tokenEndpoint()
        );
        return new OAuth2ClientCredentials(auth);
    }

    @CacheInvalidateAll(cacheName = CACHE_NAME)
    public void clearCache() {
        // Do nothing
    }
}
