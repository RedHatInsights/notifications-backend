package com.redhat.cloud.notifications.openbridge;

import io.quarkus.cache.CacheResult;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 *
 */
public class BridgeHelper {

    @ConfigProperty(name = "ob.enabled", defaultValue = "false")
    boolean obEnabled;

    @ConfigProperty(name = "ob.kcUser")
    String kcUser;
    @ConfigProperty(name = "ob.kcPass")
    String kcPass;
    @ConfigProperty(name = "ob.bridge.name")
    String ourBridge = "heiko-bridge";
    @ConfigProperty(name = "ob.token.user")
    String tokenUser;
    @ConfigProperty(name = "ob.token.pass")
    String tokenPass;

    @Inject
    @RestClient
    BridgeApiService apiService;

    @Inject
    @RestClient
    BridgeAuthService authService;


    @Produces
    public Bridge getBridgeIfNeeded() {

        if (!obEnabled) {
            return new Bridge("- OB not enabled -", "http://does.not.exist");
        }

        if (BridgeHolder.getInstance().getBridge() != null) {
            return BridgeHolder.getInstance().getBridge();
        }

        String token = getAuthTokenInternal();

        Map<String, Object> bridges = apiService.getBridges(token);
        // List of bridges is in 'items'
        List<Map<String, String>> bridgesList = (List<Map<String, String>>) bridges.get("items");

        for (Map<String, String> b : bridgesList) {
            if (b.get("name").equals(ourBridge)) {
                String bid = b.get("id");
                String ep = b.get("endpoint");

                Bridge bridge = new Bridge(bid, ep);
                BridgeHolder.getInstance().setBridge(bridge);

                return bridge;
            }
        }
        // No luck
        throw new IllegalStateException("Bridge not found");
    }

    @Produces
    public BridgeAuth getAuthToken() {
        if (!obEnabled) {
            return new BridgeAuth("- OB not enabled token -");
        }

        BridgeAuth ba = new BridgeAuth(getAuthTokenInternal());
        return ba;
    }


    // We can cache the token for a while. TODO Let's find out how long exactly.
    //    The answer to the question lies in the returned tokenMap
    @CacheResult(cacheName = "kc-cache")
    String getAuthTokenInternal() {
        String auth = getKcAuthHeader();

        String body = "username=" + tokenUser
                    + "&password=" + tokenPass
                    + "&grant_type=password";

        Map<String, Object> tokenMap = authService.getTokenStruct(body, auth);
        String authToken = (String) tokenMap.get("access_token");
        return "Bearer " + authToken;
    }

    private String getKcAuthHeader() {
        String tmp = kcUser + ":" + kcPass;
        String encoded = Base64.getEncoder().encodeToString(tmp.getBytes(StandardCharsets.UTF_8));

        return "Basic " + encoded;
    }

}
