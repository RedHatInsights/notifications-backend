package com.redhat.cloud.notifications.transformers;

import com.redhat.cloud.notifications.Severity;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;

import static com.redhat.cloud.notifications.transformers.BaseTransformer.APPLICATION;
import static com.redhat.cloud.notifications.transformers.BaseTransformer.EVENTS;
import static com.redhat.cloud.notifications.transformers.BaseTransformer.PAYLOAD;
import static com.redhat.cloud.notifications.transformers.BaseTransformer.SEVERITY;

@ApplicationScoped
public class SeverityTransformer {

    /**
     * Retrieves the severity level of the notification.
     * <br>
     * Priority:
     * <ol>
     *     <li>Top-level {@link com.redhat.cloud.notifications.ingress.Action#severity severity} value</li>
     *     <li>Migration from field of existing tenant</li>
     *     <li>Default value of {@link Severity#UNDEFINED}</li>
     * </ol>
     *
     * @param data the transformed payload
     * @return the determined severity level
     */
    public Severity getSeverity(JsonObject data) {
        String severityField = data.getString(SEVERITY);
        if (severityField != null && !severityField.isEmpty()) {
            try {
                return Severity.valueOf(severityField.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Severity.UNDEFINED;
            }
        } else {
            return extractLegacySeverity(data);
        }
    }

    /**
     * Handling of legacy severity values provided by some tenants.
     *
     * @return The highest severity within the payload events.
     */
    private Severity extractLegacySeverity(final JsonObject data) {
        JsonArray events = data.getJsonArray(EVENTS);
        if (events == null || events.isEmpty()) {
            return Severity.UNDEFINED;
        }

        ArrayList<Severity> severities = switch (data.getString(APPLICATION)) {
            case "errata-notifications" -> new ArrayList<>(events.stream().map(event -> Severity.valueOf(
                    ((JsonObject) event).getJsonObject(PAYLOAD).getString(SEVERITY).toUpperCase()
            )).toList());
            case "cluster-manager" -> new ArrayList<>(events.stream().map(event -> OcmServiceLogSeverity.valueOf(
                    ((JsonObject) event).getJsonObject(PAYLOAD).getJsonObject("global_vars").getString(SEVERITY).toUpperCase())
                    .mapToSeverity()
            ).toList());
            case "inventory" -> new ArrayList<>(events.stream().map(event -> {
                JsonObject error = ((JsonObject) event).getJsonObject(PAYLOAD).getJsonObject("error");
                // Inventory payloads only send a severity of "error", if present
                if (error != null && error.containsKey("severity") && error.getString("severity").equalsIgnoreCase("ERROR")) {
                    return Severity.IMPORTANT;
                } else {
                    return Severity.UNDEFINED;
                }
            }).toList());
            // Both OpenShift Advisor and RHEL Advisor use the same layout here
            case "advisor" -> new ArrayList<>(events.stream().map(event -> mapAdvisorTotalRiskToSeverity(
                    Integer.parseInt(((JsonObject) event).getJsonObject(PAYLOAD).getString("total_risk")))
            ).toList());
            default -> new ArrayList<>(List.of(Severity.UNDEFINED));
        };

        severities.sort(null);
        return severities.getFirst();
    }

    private Severity mapAdvisorTotalRiskToSeverity(int risk) {
        return switch (risk) {
            case 1 -> Severity.LOW;
            case 2 -> Severity.MODERATE;
            case 3 -> Severity.IMPORTANT;
            case 4 -> Severity.CRITICAL;
            default -> Severity.UNDEFINED;
        };
    }

    private enum OcmServiceLogSeverity {
        CRITICAL,
        MAJOR,
        WARNING,
        INFO,
        DEBUG;

        Severity mapToSeverity() {
            return switch (this) {
                case CRITICAL -> Severity.CRITICAL;
                case MAJOR -> Severity.IMPORTANT;
                case WARNING -> Severity.MODERATE;
                case INFO -> Severity.LOW;
                case DEBUG -> Severity.NONE;
            };
        }
    }
}

