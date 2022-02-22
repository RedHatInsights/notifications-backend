package com.redhat.cloud.notifications.temp.openbridge;

import io.quarkus.cache.CacheResult;
import io.smallrye.mutiny.Uni;
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
    String ourBridge;
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
    public Uni<com.redhat.cloud.notifications.temp.openbridge.Bridge> getBridgeIfNeeded() {

        if (!obEnabled) {
            return Uni.createFrom().item(new com.redhat.cloud.notifications.temp.openbridge.Bridge("- OB not enabled -", "http://does.not.exist"));
        }

        Uni<String> tokenUni = getAuthTokenInternal();

        return tokenUni.onItem().transformToUni(
            token -> {

                Uni<Map<String, Object>> bridgeListUni = apiService.getBridges(token);
                // List of bridges is in 'items'
                Uni<List<Map<String, String>>> bridges = bridgeListUni.onItem()
                        .transform(blu -> (List<Map<String, String>>) blu.get("items"));

                // Find our bridge in the list of bridges
                Uni<com.redhat.cloud.notifications.temp.openbridge.Bridge> bm = bridges.onItem()
                        .transform(m -> m.stream().filter(ma -> ma.get("name").equals(ourBridge))
                                .findFirst()).onItem().transform(x -> x.get())
                        .onItem().transform(bm2 -> new com.redhat.cloud.notifications.temp.openbridge.Bridge(bm2.get("id"), bm2.get("endpoint")));

                return bm;
            });
    }

    @Produces
    public Uni<com.redhat.cloud.notifications.temp.openbridge.BridgeAuth> getAuthToken() {
        if (!obEnabled) {
            return Uni.createFrom().item(new com.redhat.cloud.notifications.temp.openbridge.BridgeAuth("- OB not enabled token -"));
        }

        return getAuthTokenInternal().onItem()
                .transform(com.redhat.cloud.notifications.temp.openbridge.BridgeAuth::new);
    }


    // We can cache the token for a while. TODO Let's find out how long exactly.
    //    The answer to the question lies in the returned tokenMap
    @CacheResult(cacheName = "kc-cache")
    Uni<String> getAuthTokenInternal() {
        String auth = getKcAuthHeader();

        String body = "username=" + tokenUser
                    + "&password=" + tokenPass
                    + "&grant_type=password";

        Uni<Map<String, Object>> tokenMap = authService.getTokenStruct(body, auth);
        return tokenMap.onItem().transform(map -> map.get("access_token"))
                       .onItem().transform(s -> "Bearer " + s);    }

    private String getKcAuthHeader() {
        String tmp = kcUser + ":" + kcPass;
        String encoded = Base64.getEncoder().encodeToString(tmp.getBytes(StandardCharsets.UTF_8));

        return "Basic " + encoded;
    }

}
