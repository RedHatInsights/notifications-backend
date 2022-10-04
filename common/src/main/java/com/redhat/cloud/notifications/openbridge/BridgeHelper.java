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
import javax.ws.rs.NotFoundException;
import javax.ws.rs.WebApplicationException;
import java.util.HashMap;
import java.util.Map;

/**
 *
 */
@ApplicationScoped
public class BridgeHelper {

    public static final String ORG_ID_FILTER_NAME = "rhorgid";
    public static final String CLOUD_PROVIDER = "aws";
    public static final String CLOUD_REGION = "us-east-1";

    @Inject
    FeatureFlipper featureFlipper;

    @ConfigProperty(name = "ob.bridge.name")
    String ourBridgeName;
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

    @Inject
    BridgeAuth bridgeAuth;

    private Bridge bridgeInstance;

    @Produces
    @ApplicationScoped
    public Bridge getBridgeIfNeeded() {

        if (!featureFlipper.isObEnabled()) {
            return new Bridge("- OB not enabled -", "http://does.not.exist", "no name");
        }

        if (bridgeInstance != null) {
            return bridgeInstance;
        }

        String token;

        try {
            token = bridgeAuth.getToken();
        } catch (Exception e) {
            Log.errorf("Failed to get an auth token: %s", e.getMessage());
            throw e;
        }

        BridgeItemList<Bridge> bridgeList = null;
        try {
            bridgeList = apiService.getBridgeByName(ourBridgeName, token);
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == 404) {
                Log.errorf("Bridge with name %s not found in the OpenBridge instance. Did you create it?", ourBridgeName);
            }
        }

        // The name= query can return more than one instance, as it is a prefix match.
        // See MGDOBR-1112
        if (bridgeList != null) {
            for (Bridge b : bridgeList.getItems()) {
                if (b.getName().equals(ourBridgeName)) {
                    bridgeInstance = b;
                    return b;
                }
            }
        }

        // Bridge seems not yet created, so let's try to do so
        Log.warnf("Bridge with name %s not found in the OpenBridge instance. We will try to create it", ourBridgeName);
        try {
            Bridge bridge = createNewBridge(token);
            bridgeInstance = bridge;
            return bridge;
        } catch (Exception e) {
            Log.error("Bridge creation failed:", e);
            throw new NotFoundException("No bridge found");
        }
    }

    @Produces
    @RequestScoped
    public BridgeAuth getAuthToken() {
        if (!featureFlipper.isObEnabled()) {
            return new BridgeAuth("- OB not enabled token -");
        }

        BridgeAuth ba;
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
        return authToken;
    }

    Bridge createNewBridge(String token) {
        BridgeRequest request = new BridgeRequest(ourBridgeName, CLOUD_PROVIDER, CLOUD_REGION);
        Map<String, Object> handler = new HashMap<>();
        handler.put("type", "endpoint"); // endpoint is using the poller
        request.setErrorHandler(handler);

        apiService.createBridge(token, request);
        Log.warn("Bridge creation initiated. It may take a while until it is ready");
        BridgeItemList<Bridge> bridgeList = apiService.getBridgeByName(ourBridgeName, token);

        return bridgeList.getItems().get(0);
    }

    // Test helper
    public void setOurBridgeName(String name) {
        ourBridgeName = name;
        bridgeInstance = null;
    }
}
