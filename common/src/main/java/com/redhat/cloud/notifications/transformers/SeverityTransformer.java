package com.redhat.cloud.notifications.transformers;

import com.redhat.cloud.notifications.Severity;
import com.redhat.cloud.notifications.events.EventWrapperAction;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Event;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

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
        Severity severity = event.getEventType().getDefaultSeverity();
        if ((event.getEventWrapper() instanceof EventWrapperAction action)) {
            Optional<Severity> severityFromIncomingEvent = getSeverity(action.getEvent());
            if (severityFromIncomingEvent.isPresent()) {
                if (null != event.getEventType().getAvailableSeverities()
                    && event.getEventType().getAvailableSeverities().contains(severityFromIncomingEvent.get())) {
                    severity = severityFromIncomingEvent.get();
                } else {
                    Log.infof("Severity '%s' is not available for event type '%s' of application '%s', '%s' will be used as default value",
                        severityFromIncomingEvent.get(),
                        event.getEventType().getName(),
                        event.getEventType().getApplication().getName(),
                        event.getEventType().getDefaultSeverity());
                }
            }
        }
        return severity;
    }

    /**
     * Retrieves the severity level from an Action.
     * <br>
     * Priority:
     * <ol>
     *     <li>Top-level {@link Action#severity severity} value</li>
     *     <li>Migration from field of existing tenant</li>
     *     <li>Default value of {@link Severity#UNDEFINED}</li>
     * </ol>
     */
    public Optional<Severity> getSeverity(Action action) {
        if (action.getSeverity() != null) {
            try {
                return Optional.of(Severity.valueOf(action.getSeverity().toUpperCase()));
            } catch (IllegalArgumentException ex) {
                Log.errorf(ex, "Invalid severity: %s", action.getSeverity());
                return Optional.empty();
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
    private Optional<Severity> extractLegacySeverity(final Action action) {
        List<Event> events = action.getEvents();
        Optional<Severity> severity = Optional.empty();
        if (!events.isEmpty()) {

            Stream<Severity> severities = switch (action.getApplication()) {
                // Both OpenShift Advisor and RHEL Advisor use the same layout here
                case "advisor" -> events.stream().map(
                    event -> {
                        try {
                            return extractAdvisorTotalRiskFromEvent(event);
                        } catch (Exception ex) {
                            Log.errorf(ex, "Error extracting 'advisor' severity from event '%s'", event);
                            return null;
                        }
                    }
                );
                case "cluster-manager" -> events.stream().map(event -> {
                    try {
                        return OcmServiceLogSeverity.valueOf(
                            ((Map<String, Object>) event.getPayload().getAdditionalProperties().get("global_vars"))
                                .get(SEVERITY).toString().toUpperCase()
                        ).getSeverity();
                    } catch (Exception ex) {
                        Log.errorf(ex, "Error extracting 'cluster-manager' severity from event '%s'", event);
                        return null;
                    }
                });
                case "errata-notifications" -> events.stream().map(event -> {
                    try {
                        return Severity.valueOf(
                            event.getPayload().getAdditionalProperties().get(SEVERITY).toString().toUpperCase()
                        );
                    } catch (Exception ex) {
                        Log.errorf(ex, "Error extracting 'errata-notifications' severity from event '%s'", event);
                        return null;
                    }
                });
                default -> Stream.empty();
            };

            List<Severity> extractedSeverities = new ArrayList<>(severities.filter(Objects::nonNull).toList());
            if (!extractedSeverities.isEmpty()) {
                extractedSeverities.sort(null);
                severity = Optional.of(extractedSeverities.getFirst());
            }
        }
        return severity;
    }

    Severity extractAdvisorTotalRiskFromEvent(Event event) {
        Object totalRisk = event.getPayload().getAdditionalProperties().get("total_risk");
        Severity severity = null;
        Integer totalRiskInt = null;
        if (totalRisk instanceof Integer) {
            totalRiskInt = (Integer) totalRisk;
        } else if (totalRisk instanceof String) {
            try {
                totalRiskInt = Integer.parseInt((String) totalRisk);
            } catch (NumberFormatException nfe) {
                Log.error("Invalid number format for Advisor totalRisk", nfe);
                return null;
            }
        }

        if (totalRiskInt != null) {
            severity = switch (totalRiskInt) {
                case 4 -> Severity.CRITICAL;
                case 3 -> Severity.IMPORTANT;
                case 2 -> Severity.MODERATE;
                case 1 -> Severity.LOW;
                case 0 -> Severity.NONE;
                default -> null;
            };
        }
        return severity;
    }

    enum OcmServiceLogSeverity {
        CRITICAL(Severity.CRITICAL),
        MAJOR(Severity.IMPORTANT),
        WARNING(Severity.MODERATE),
        INFO(Severity.LOW),
        DEBUG(Severity.NONE);

        private final Severity severity;

        OcmServiceLogSeverity(final Severity severity) {
            this.severity = severity;
        }

        public Severity getSeverity() {
            return severity;
        }
    }
}
