package com.redhat.cloud.notifications.transformers;

import com.redhat.cloud.notifications.Severity;
import com.redhat.cloud.notifications.events.EventWrapperAction;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Event;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.redhat.cloud.notifications.transformers.BaseTransformer.SEVERITY;

@ApplicationScoped
public class SeverityTransformer {

        /**
     * Retrieves the severity level of the notification.
     * <br>
     * Priority:
     * <ol>
     *     <li>Top-level {@link Action#severity severity} value</li>
     *     <li>Migration from field of existing tenant</li>
     *     <li>Default value of {@link Severity#UNDEFINED}</li>
     * </ol>
     */
    public Severity getSeverity(com.redhat.cloud.notifications.models.Event event) {
        if (!(event.getEventWrapper() instanceof EventWrapperAction)) {
            return Severity.UNDEFINED;
        }
        Action action = ((EventWrapperAction) event.getEventWrapper()).getEvent();

        if (action.getSeverity() != null) {
            try {
                return Severity.valueOf(action.getSeverity().toUpperCase());
            } catch (IllegalArgumentException e) {
                return Severity.UNDEFINED;
            }
        } else {
            return extractLegacySeverity(action);
        }
    }

    /**
     * Handling of legacy severity values provided by some tenants.
     *
     * @return The highest severity within the payload events.
     */
    private Severity extractLegacySeverity(final Action action) {
        List<Event> events = action.getEvents();
        if (events.isEmpty()) {
            return Severity.UNDEFINED;
        }

        ArrayList<Severity> severities = switch (action.getApplication()) {
            // Both OpenShift Advisor and RHEL Advisor use the same layout here
            case "advisor" -> new ArrayList<>(events.stream().map(
                    event -> {
                        try {
                            return AdvisorTotalRisk.fromEvent(event).mapToSeverity();
                        } catch (Exception e) {
                            return Severity.UNDEFINED;
                        }
                    }
            ).toList());
            case "cluster-manager" -> new ArrayList<>(events.stream().map(event -> {
                try {
                    return OcmServiceLogSeverity.valueOf(
                            ((Map<String, Object>) event.getPayload().getAdditionalProperties().get("global_vars"))
                                    .get(SEVERITY).toString().toUpperCase()
                    ).mapToSeverity();
                } catch (Exception ignored) {
                    return Severity.UNDEFINED;
                }
            }).toList());
            case "errata-notifications" -> new ArrayList<>(events.stream().map(event -> {
                try {
                    return Severity.valueOf(
                            event.getPayload().getAdditionalProperties().get(SEVERITY).toString().toUpperCase()
                    );
                } catch (Exception ignored) {
                    return Severity.UNDEFINED;
                }
            }).toList());
            case "inventory" -> {
                if (action.getEventType().equals("validation-error")) {
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

    enum AdvisorTotalRisk {
        CRITICAL_RISK, // 4
        HIGH_RISK, // 3
        MEDIUM_RISK, // 2
        LOW_RISK, // 1
        NOTHING, // 0
        UNKNOWN; // < 0 or > 4

        static AdvisorTotalRisk fromEvent(Event event) {
            Object totalRisk = event.getPayload().getAdditionalProperties().get("total_risk");
            int totalRiskInt;
            if (totalRisk instanceof Integer) {
                totalRiskInt = (Integer) totalRisk;
            } else if (totalRisk instanceof String) {
                try {
                    totalRiskInt = Integer.parseInt((String) totalRisk);
                } catch (NumberFormatException ignored) {
                    return UNKNOWN;
                }
            } else {
                return UNKNOWN;
            }

            return switch (totalRiskInt) {
                case 4 -> CRITICAL_RISK;
                case 3 -> HIGH_RISK;
                case 2 -> MEDIUM_RISK;
                case 1 -> LOW_RISK;
                case 0 -> NOTHING;
                default -> UNKNOWN;
            };
        }

        Severity mapToSeverity() {
            return switch (this) {
                case CRITICAL_RISK -> Severity.CRITICAL;
                case HIGH_RISK -> Severity.IMPORTANT;
                case MEDIUM_RISK -> Severity.MODERATE;
                case LOW_RISK -> Severity.LOW;
                case NOTHING -> Severity.NONE;
                case UNKNOWN -> Severity.UNDEFINED;
            };
        }
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

