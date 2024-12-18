package com.redhat.cloud.notifications.processors;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.Environment;
import com.redhat.cloud.notifications.models.Event;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
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
     *     <li>{@code { "context": { "display_name": "non_empty_string" } }}</li>
     *     <li>{@code { "context": { "inventory_id": "non_empty_string" }}}</li>
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
        ArrayList<String> queryParamParts = new ArrayList<>();

        String environmentUrl = environment.url();
        String displayName = data.getString("display_name", "");
        String inventoryId = data.getString("inventory_id", "");

        if (!displayName.isEmpty()
                && data.getString("bundle", "").equals("openshift")
                && data.getString("application", "").equals("advisor")) {
            path = String.format("/openshift/insights/advisor/clusters/%s", displayName);
        } else {
            path = "/insights/inventory/";
            if (!inventoryId.isEmpty()) {
                path += inventoryId;
            } else if (!displayName.isEmpty()) {
                queryParamParts.add(String.format("hostname_or_id=%s", displayName));
            } else {
                return Optional.empty();
            }
        }

        if (!queryParamParts.isEmpty()) {
            String queryParams = "?" + String.join("&", queryParamParts);
            path += queryParams;
        }

        return Optional.of(environmentUrl + path);
    }

    /**
     * <p>Constructs an Insights URL corresponding to the specific inventory item which generated the notification.</p>
     *
     * <p>If the required field {@link Action#getApplication()} is not present, an
     * {@link Optional#empty()} will be returned. If the expected field {@link Action#getBundle()} is not present, an
     * inaccurate URL may be returned.</p>
     *
     * @param data a payload converted by
     *             {@link com.redhat.cloud.notifications.transformers.BaseTransformer#toJsonObject(Event) BaseTransformer#toJsonObject(Event)}
     * @return URL to the generating application, if required fields are present
     */
    public Optional<String> buildApplicationUrl(JsonObject data) {
        String path = "";

        String environmentUrl = environment.url();
        String bundle = data.getString("bundle", "");
        String application;

        if (data.containsKey("application") && !data.getString("application", "").isEmpty()) {
            application = data.getString("application");
        } else {
            return Optional.empty();
        }

        if (bundle.equals("application-services") && application.equals("rhosak")) {
            path = "application-services/streams";
        } else {
            if (bundle.equals("openshift")) {
                path = "openshift/";
            }
            path += "insights/" + application;
        }

        return Optional.of(String.format("%s/%s", environmentUrl, path));
    }
}
