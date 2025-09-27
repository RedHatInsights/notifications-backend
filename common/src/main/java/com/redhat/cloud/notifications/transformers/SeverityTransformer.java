package com.redhat.cloud.notifications.transformers;

import com.redhat.cloud.event.parser.ConsoleCloudEventParser;
import com.redhat.cloud.event.parser.exceptions.ConsoleCloudEventParsingException;
import com.redhat.cloud.notifications.Severity;
import com.redhat.cloud.notifications.events.EventWrapper;
import com.redhat.cloud.notifications.events.EventWrapperAction;
import com.redhat.cloud.notifications.events.EventWrapperCloudEvent;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.NotificationsConsoleCloudEvent;
import com.redhat.cloud.notifications.utils.ActionParser;
import com.redhat.cloud.notifications.utils.ActionParsingException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

import static com.redhat.cloud.notifications.transformers.BaseTransformer.APPLICATION;
import static com.redhat.cloud.notifications.transformers.BaseTransformer.EVENTS;
import static com.redhat.cloud.notifications.transformers.BaseTransformer.EVENT_TYPE;
import static com.redhat.cloud.notifications.transformers.BaseTransformer.PAYLOAD;
import static com.redhat.cloud.notifications.transformers.BaseTransformer.SEVERITY;

@ApplicationScoped
public class SeverityTransformer {

    @Inject
    BaseTransformer baseTransformer;

    @Inject
    ActionParser actionParser;

    ConsoleCloudEventParser cloudEventParser = new ConsoleCloudEventParser();

    /**
     * Retrieves the severity level of the notification.
     * <br>
     * Priority:
     * <ol>
     *     <li>Top-level {@link com.redhat.cloud.notifications.ingress.Action#severity severity} value</li>
     *     <li>Migration from field of existing tenant</li>
     *     <li>Default value of {@link Severity#UNDEFINED}</li>
     * </ol>
     */
    public Severity getSeverity(Event event) {
        if (event.getEventWrapper() == null) {
            event.setEventWrapper(getEventWrapper(event.getPayload()));
        }
        return getSeverity(baseTransformer.toJsonObject(event));
    }

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
     * @param data a payload processed by {@link BaseTransformer}
     */
    public Severity getSeverity(JsonObject data) {
        String severityField = data.getString(SEVERITY);
        if (severityField != null && !severityField.isEmpty()) {
            try {
                return Severity.valueOf(severityField);
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
            // Both OpenShift Advisor and RHEL Advisor use the same layout here
            case "advisor" -> new ArrayList<>(events.stream().map(
                    event -> {
                        try {
                            return mapAdvisorTotalRiskToSeverity(parseAdvisorTotalRisk((JsonObject) event));
                        } catch (Exception e) {
                            return Severity.UNDEFINED;
                        }
                    }
            ).toList());
            case "cluster-manager" -> new ArrayList<>(events.stream().map(event -> {
                try {
                    return OcmServiceLogSeverity.valueOf(
                                    ((JsonObject) event).getJsonObject(PAYLOAD).getJsonObject("global_vars").getString(SEVERITY).toUpperCase())
                            .mapToSeverity();
                } catch (Exception ignored) {
                    return Severity.UNDEFINED;
                }
            }).toList());
            case "errata-notifications" -> new ArrayList<>(events.stream().map(event -> {
                try {
                    return Severity.valueOf(
                            ((JsonObject) event).getJsonObject(PAYLOAD).getString(SEVERITY).toUpperCase()
                    );
                } catch (Exception ignored) {
                    return Severity.UNDEFINED;
                }
            }).toList());
            case "inventory" -> {
                if (data.getString(EVENT_TYPE).equals("validation-error")) {
                    yield new ArrayList<>(List.of(Severity.IMPORTANT));
                } else {
                    yield new ArrayList<>(List.of(Severity.UNDEFINED));
                }
            }
            default -> new ArrayList<>(List.of(Severity.UNDEFINED));
        };

        severities.sort(null);
        return severities.getFirst();
    }

    private EventWrapper<?, ?> getEventWrapper(String payload) {
        try {
            Action action = actionParser.fromJsonString(payload);
            return new EventWrapperAction(action);
        } catch (ActionParsingException actionParseException) {
            // Try to load it as a CloudEvent
            try {
                return new EventWrapperCloudEvent(cloudEventParser.fromJsonString(payload, NotificationsConsoleCloudEvent.class));
            } catch (ConsoleCloudEventParsingException cloudEventParseException) {
                actionParseException.addSuppressed(cloudEventParseException);
                throw actionParseException;
            }
        }
    }

    private Integer parseAdvisorTotalRisk(JsonObject event) {
        Object totalRisk = event.getJsonObject(PAYLOAD).getValue("total_risk");
        if (totalRisk instanceof Integer) {
            return (Integer) totalRisk;
        } else if (totalRisk instanceof String) {
            try {
                return Integer.parseInt((String) totalRisk);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        } else {
            return 0;
        }
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

    enum OcmServiceLogSeverity {
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

