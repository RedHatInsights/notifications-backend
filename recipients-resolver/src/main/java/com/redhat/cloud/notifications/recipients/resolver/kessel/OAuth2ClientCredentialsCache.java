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

        String issuer = recipientsResolverConfig.getKesselClientIssuer()
            .orElseThrow(() -> new IllegalStateException("Missing required configuration: notifications.kessel.authn.issuer"));
        String clientId = recipientsResolverConfig.getKesselClientId()
            .orElseThrow(() -> new IllegalStateException("Missing required configuration: notifications.kessel.authn.client-id"));
        String clientSecret = recipientsResolverConfig.getKesselClientSecret()
            .orElseThrow(() -> new IllegalStateException("Missing required configuration: notifications.kessel.authn.client-secret"));

        OIDCDiscoveryMetadata discoveryMetadata = OIDCDiscovery.fetchOIDCDiscovery(issuer);
        ClientConfigAuth auth = new ClientConfigAuth(clientId, clientSecret, discoveryMetadata.tokenEndpoint());
        return new OAuth2ClientCredentials(auth);
    }

    @CacheInvalidateAll(cacheName = CACHE_NAME)
    public void clearCache() {
        // Do nothing
    }
}
