package com.redhat.cloud.notifications.recipients.resolver.kessel;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.project_kessel.api.auth.OAuth2ClientCredentials;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the OAuth2-authenticated branch of initializeChannel(), which
 * KesselInventoryClientTest cannot reach since it forces insecure mode.
 */
@QuarkusTest
@TestProfile(KesselInventoryClientOAuth2Test.OAuth2KesselProfile.class)
public class KesselInventoryClientOAuth2Test {

    public static class OAuth2KesselProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                "notifications.kessel.insecure-client.enabled", "false",
                "notifications.kessel.url", "localhost:9999",
                "notifications.kessel.timeout-ms", "5000",
                "notifications.kessel.authn.client-id", "test-client-id",
                "notifications.kessel.authn.client-secret", "test-client-secret",
                "notifications.kessel.authn.issuer", "http://localhost:9999/realms/test"
            );
        }
    }

    @Inject
    KesselInventoryClient kesselInventoryClient;

    @InjectMock
    OAuth2ClientCredentialsCache oauth2ClientCredentialsCache;

    @Test
    void testInitializeChannelUsesOAuth2CredentialsWhenNotInsecure() throws Exception {
        when(oauth2ClientCredentialsCache.getCredentials()).thenReturn(mock(OAuth2ClientCredentials.class));

        // Triggers lazy bean creation -> @PostConstruct -> initializeChannel() on the OAuth2 branch.
        KesselInventoryClient actualBean = ClientProxy.unwrap(kesselInventoryClient);

        // Startup init must not force a cache clear: a cached, still-valid token should be reused.
        verify(oauth2ClientCredentialsCache, never()).clearCache();
        verify(oauth2ClientCredentialsCache, atLeastOnce()).getCredentials();

        Field channelField = KesselInventoryClient.class.getDeclaredField("grpcChannel");
        channelField.setAccessible(true);
        assertNotNull(channelField.get(actualBean));

        Field clientField = KesselInventoryClient.class.getDeclaredField("grpcClient");
        clientField.setAccessible(true);
        assertNotNull(clientField.get(actualBean));

        // Unlike startup, a real UNAUTHENTICATED error must force a fresh credentials fetch.
        // Kept in the same test (rather than a separate @Test) since KesselInventoryClient is an
        // @ApplicationScoped singleton within this test class: @PostConstruct only fires once per
        // class, so a second test touching the bean would race for that one-time trigger.
        kesselInventoryClient.handleGrpcException(new StatusRuntimeException(Status.UNAUTHENTICATED));
        verify(oauth2ClientCredentialsCache, atLeastOnce()).clearCache();
    }
}
