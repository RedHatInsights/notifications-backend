package com.redhat.cloud.notifications.processors;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.Environment;
import com.redhat.cloud.notifications.models.Event;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class InsightsUrlsBuilder {

    @Inject
    Environment environment;

    /**
     * <p>Constructs an Insights URL corresponding to the specific inventory item which generated the notification.</p>
     *
     * <p>An inventory URL will only be generated if fields from one of these two formats are present:</p>
     *
     * <ul>
     *     <li>{@code { "context": { "host_url": "non_empty_string" } }}</li>
     *     <li>{@code { "context": { "inventory_id": "non_empty_string" }}}</li>
     *     <li>{@code { "context": { "display_name": "non_empty_string" } }}</li>
     * </ul>
     *
     * <p>If neither field is present, an {@link Optional#empty()} will be returned. If expected fields of
     * {@link Action#getBundle()} or {@link Action#getApplication()} are missing, an inaccurate URL may be returned.</p>
     *
     * @param data a payload converted by
     *             {@link com.redhat.cloud.notifications.transformers.BaseTransformer#toJsonObject(Event) BaseTransformer#toJsonObject(Event)}
     * @return URL to the generating inventory item, if required fields are present
     */
    public Optional<String> buildInventoryUrl(JsonObject data) {
        String path;
        ArrayList<String> queryParamParts = new ArrayList<>(List.of("from=notifications"));
        JsonObject context = data.getJsonObject("context");
        if (context == null) {
            return Optional.empty();
        }

        // A provided host url does not need to be modified
        String host_url = context.getString("host_url", "");
        if (!host_url.isBlank()) {
            return Optional.of(host_url + "?" + String.join("&", queryParamParts));
        }

        String environmentUrl = environment.url();
        String inventoryId = context.getString("inventory_id", "");
        String displayName = context.getString("display_name", "");

        if (!displayName.isBlank()) {
            if (data.getString("bundle", "").equals("openshift")
                    && data.getString("application", "").equals("advisor")) {
                path = String.format("/openshift/insights/advisor/clusters/%s", displayName);
            } else {
                path = "/insights/inventory/";
                if (!inventoryId.isBlank()) {
                    path += inventoryId;
                } else {
                    queryParamParts.add(String.format("hostname_or_id=%s", displayName));
                }
            }
        } else {
            return Optional.empty();
        }

        return Optional.of(environmentUrl + path + "?" + String.join("&", queryParamParts));
    }

    /**
     * <p>Constructs an Insights URL corresponding to the specific inventory item which generated the notification.</p>
     *
     * <p>If the expected fields {@link Action#getApplication()} and {@link Action#getBundle()} are not present, an
     * inaccurate URL may be returned.</p>
     *
     * @param data a payload converted by
     *             {@link com.redhat.cloud.notifications.transformers.BaseTransformer#toJsonObject(Event) BaseTransformer#toJsonObject(Event)}
     * @return URL to the generating application
     */
    public String buildApplicationUrl(JsonObject data) {
        String path = "";
        ArrayList<String> queryParamParts = new ArrayList<>(List.of("from=notifications"));

        String environmentUrl = environment.url();
        String bundle = data.getString("bundle", "");
        String application = data.getString("application", "");

        if (bundle.equals("openshift")) {
            path = "openshift/";
        }

        if (application.equals("integrations")) {
            path += "settings/";
        } else {
            path += "insights/";
        }

        path += application;
        if (!queryParamParts.isEmpty()) {
            String queryParams = "?" + String.join("&", queryParamParts);
            path += queryParams;
        }

        return String.format("%s/%s", environmentUrl, path);
    }
}
