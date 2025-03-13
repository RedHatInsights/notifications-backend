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
     * @param integration_type a string used to construct the source query param. Inputs will be converted to lowercase,
     *                         and spaces replaced with underscores. For example, {@code "Google Chat"} becomes {@code from=notification_google_chat}.
     * @return URL to the generating inventory item, if required fields are present
     */
    public Optional<String> buildInventoryUrl(JsonObject data, String integration_type) {
        String path;
        ArrayList<String> queryParamParts = new ArrayList<>();
        JsonObject context = data.getJsonObject("context");
        if (context == null) {
            return Optional.empty();
        }

        // A provided host url does not need to be modified
        String host_url = context.getString("host_url", "");
        if (!host_url.isBlank()) {
            return Optional.of(host_url + buildQueryParams(queryParamParts, integration_type));
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

        return Optional.of(environmentUrl + path + buildQueryParams(queryParamParts, integration_type));
    }

    /**
     * <p>Constructs an Insights URL corresponding to the specific inventory item which generated the notification.</p>
     *
     * <p>If the expected fields {@link Action#getApplication()}, {@link Action#getBundle()}, and {@link Action#getEventType()}
     *  are not present, an inaccurate URL may be returned.</p>
     *
     * @param data a payload converted by
     *             {@link com.redhat.cloud.notifications.transformers.BaseTransformer#toJsonObject(Event) BaseTransformer#toJsonObject(Event)}
     * @param integration_type a string used to construct the source query param (ex. {@code from=notification_instant_email})
     * @return URL to the generating application
     */
    public String buildApplicationUrl(JsonObject data, String integration_type) {
        String path = "";

        String environmentUrl = environment.url();
        String bundle = data.getString("bundle", "");
        String application = data.getString("application", "");

        if (bundle.equals("openshift")) {
            path = "openshift/";
        } else if (bundle.equals("ansible-automation-platform")) {
            path = "ansible/";
        }

        path = switch (application) {
            // Hard override
            case "rbac" -> "iam/user-access/users";
            case "edge-management" -> "edge";
            // Settings paths
            case "integrations", "notifications" -> path + "settings/" + application;
            // OpenShift path override
            case "cluster-manager" -> path;
            case "cost-management" -> path + application;
            // Ansible Automation Platform path override
            case "ansible-service-on-aws" -> path + "/service/instances";
            // RHEL path override
            case "malware-detection" -> path + "insights/malware";
            case "resource-optimization" -> path + "insights/" + "ros";
            default -> path + "insights/" +  application;
        };

        return environmentUrl + "/" + path + buildQueryParams(List.of(), integration_type);
    }

    /**
     * Build query parameters from provided arguments, including default parameters. This method should only be called
     * directly if the URL will be assembled by the endpoint application.
     *
     * @param params Any non default parameters to be used
     * @param integration_type a string used to construct the source query param (ex. {@code from=notification_instant_email})
     * @return formatted query parameters
     */
    public String buildQueryParams(List<String> params, String integration_type) {
        List<String> all_params = new ArrayList<>(List.copyOf(params));
        all_params.add("from=notification_" + integration_type.toLowerCase());

        return "?" + String.join("&", all_params);
    }
}
