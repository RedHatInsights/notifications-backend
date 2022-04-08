package com.redhat.cloud.notifications.openbridge;

import io.quarkus.cache.CacheResult;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import java.util.Map;

/**
 *
 */
@ApplicationScoped
public class BridgeHelper {

    @ConfigProperty(name = "ob.enabled", defaultValue = "false")
    boolean obEnabled;

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

    private static final Logger LOGGER = Logger.getLogger(BridgeHelper.class);

    @ApplicationScoped
    @Produces
    public Bridge getBridgeIfNeeded() {

        if (!obEnabled) {
            return new Bridge("- OB not enabled -", "http://does.not.exist", "no name");
        }

        if (bridgeInstance != null) {
            return bridgeInstance;
        }

        String token = getAuthTokenInternal();

        Map<String, String> bridgeMap;
        try {
            bridgeMap = apiService.getBridgeById(ourBridge, token);
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == 404) {
                LOGGER.errorf("Bridge with id %s not found in the OpenBridge instance. Did you create it?", ourBridge);
            }
            throw e;
        }

        String bid = bridgeMap.get("id");
        String ep = bridgeMap.get("endpoint");
        if (ep.endsWith("/events")) {
            ep = ep.substring(0, ep.lastIndexOf("/")); // OB is tacking that on at some point in the future, be prepared for the transition period.
        }
        String name = bridgeMap.get("name");

        Bridge bridge = new Bridge(bid, ep, name);
        bridgeInstance = bridge;

        return bridge;
    }

    @ApplicationScoped
    @Produces
    public BridgeAuth getAuthToken() {
        if (!obEnabled) {
            return new BridgeAuth("- OB not enabled token -");
        }

        BridgeAuth ba = null;
        try {
            ba = new BridgeAuth(getAuthTokenInternal());
        } catch (Exception e) {
            LOGGER.warn("Failed to get an auth token: " + e.getMessage());
            ba = new BridgeAuth("- No token - obtained -");
        }
        return ba;
    }


    // We can cache the token for up to 15 minutes
    @CacheResult(cacheName = "kc-cache")
    String getAuthTokenInternal() {

        String body = "client_id=" + clientId
                    + "&client_secret=" + clientSecret
                    + "&grant_type=client_credentials";

        Map<String, Object> tokenMap = authService.getTokenStructWithClientCredentials(body);
        String authToken = (String) tokenMap.get("access_token");
        return "Bearer " + authToken;
    }

    public void setObEnabled(boolean obEnabled) {
        this.obEnabled = obEnabled;
    }

    public void setOurBridge(String id) {
        ourBridge = id;
    }
}
