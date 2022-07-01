package com.redhat.cloud.notifications.openbridge;

import com.redhat.cloud.notifications.config.FeatureFlipper;
import io.quarkus.cache.CacheResult;
import io.quarkus.logging.Log;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import java.util.Map;

/**
 *
 */
@ApplicationScoped
public class BridgeHelper {

    @Inject
    FeatureFlipper featureFlipper;

    @ConfigProperty(name = "ob.bridge.uuid")
    String ourBridge;
    @ConfigProperty(name = "ob.token.client.secret")
    String clientSecret;
    @ConfigProperty(name = "ob.token.client.id")
    String clientId;

    @Inject
    @RestClient
    BridgeApiService apiService;

    @Inject
    @RestClient
    BridgeAuthService authService;

    private Bridge bridgeInstance;

    @ApplicationScoped
    @Produces
    public Bridge getBridgeIfNeeded() {

        if (!featureFlipper.isObEnabled()) {
            return new Bridge("- OB not enabled -", "http://does.not.exist", "no name");
        }

        if (bridgeInstance != null) {
            return bridgeInstance;
        }

        String token;

        try {
            token = getAuthTokenInternal();
        } catch (Exception e) {
            Log.errorf("Failed to get an auth token: %s", e.getMessage());
            throw e;
        }

        Map<String, String> bridgeMap;
        try {
            bridgeMap = apiService.getBridgeById(ourBridge, token);
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == 404) {
                Log.errorf("Bridge with id %s not found in the OpenBridge instance. Did you create it?", ourBridge);
            }
            throw e;
        }

        String bid = bridgeMap.get("id");
        String ep = bridgeMap.get("endpoint");
        String name = bridgeMap.get("name");

        Bridge bridge = new Bridge(bid, ep, name);
        bridgeInstance = bridge;

        return bridge;
    }

    @RequestScoped
    @Produces
    public BridgeAuth getAuthToken() {
        if (!featureFlipper.isObEnabled()) {
            return new BridgeAuth("- OB not enabled token -");
        }

        Log.debug("In getAuthToken()");

        BridgeAuth ba = null;
        try {
            ba = new BridgeAuth(getAuthTokenInternal());
        } catch (Exception e) {
            Log.warn("Failed to get an auth token: " + e.getMessage());
            ba = new BridgeAuth("- No token - obtained -");
        }
        return ba;
    }


    // We can cache the token for up to 15 minutes
    @CacheResult(cacheName = "kc-cache")
    String getAuthTokenInternal() {

        Log.debug("Fetching a new token from SSO");

        String body = "client_id=" + clientId
                    + "&client_secret=" + clientSecret
                    + "&grant_type=client_credentials";

        Map<String, Object> tokenMap = authService.getTokenStructWithClientCredentials(body);
        String authToken = (String) tokenMap.get("access_token");
        return "Bearer " + authToken;
    }

    public void setOurBridge(String id) {
        ourBridge = id;
    }
}
