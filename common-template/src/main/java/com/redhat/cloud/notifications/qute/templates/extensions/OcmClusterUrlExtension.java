package com.redhat.cloud.notifications.qute.templates.extensions;

import io.quarkus.qute.TemplateExtension;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.Map;

public class OcmClusterUrlExtension {

    @TemplateExtension(matchName = "ocm_cluster_path")
    public static String getClusterUrlPath(Map<String, Object> dataMap) {
        JsonObject data = JsonObject.mapFrom(dataMap);
        String subscriptionId = getSubscriptionId(data);

        if (subscriptionId != null) {
            return "openshift/details/s/" + subscriptionId;
        } else {
            return  "/openshift/cluster-list";
        }
    }

    /** @return the value of {@code .events[0].payload.global_vars.subscription_id}, or null if it doesn't exist */
    private static String getSubscriptionId(final JsonObject data) {
        JsonObject firstEvent = data.getJsonArray("events", JsonArray.of())
                .getJsonObject(0);
        if (firstEvent != null) {
            return firstEvent.getJsonObject("payload", JsonObject.of())
                    .getJsonObject("global_vars", JsonObject.of())
                    .getString("subscription_id");
        }

        return null;
    }
}
