package com.redhat.cloud.notifications.unleash;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.getunleash.Unleash;
import io.getunleash.Variant;
import io.getunleash.repository.ToggleCollection;
import io.getunleash.variant.Payload;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.FINER;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;
import static java.util.stream.Collectors.toSet;

@ApplicationScoped
public class LogLevelManager {

    private static final String UNLEASH_TOGGLE_NAME = "notifications.log-levels";
    private static final String INHERITED = "INHERITED";

    @ConfigProperty(name = "host-name", defaultValue = "localhost")
    String hostName;

    @Inject
    Unleash unleash;

    @Inject
    ObjectMapper objectMapper;

    private final Map<String, Level> previousLogLevels = new HashMap<>();

    public void process(@Observes ToggleCollection toggleCollection) {
        try {

            Variant variant = unleash.getVariant(UNLEASH_TOGGLE_NAME);
            if (variant.isEnabled()) {
                Optional<Payload> payload = variant.getPayload();

                if (payload.isEmpty()) {
                    Log.warn("Variant ignored because of an empty payload");
                    return;
                }

                if (!payload.get().getType().equals("json")) {
                    Log.warnf("Variant ignored because of a wrong payload type [expected=json, actual=%s]", payload.get().getType());
                    return;
                }

                if (payload.get().getValue() == null) {
                    Log.warn("Variant ignored because of a null payload value");
                    return;
                }

                LogConfig[] logConfigs;
                try {
                    logConfigs = objectMapper.readValue(payload.get().getValue(), LogConfig[].class);
                } catch (JsonProcessingException e) {
                    Log.warn("Variant payload deserialization failed", e);
                    return;
                }

                for (LogConfig logConfig : logConfigs) {
                    if (shouldCurrentHostBeUpdated(logConfig)) {

                        Optional<Level> newLevel = getLogLevel(logConfig.level);

                        if (newLevel.isEmpty()) {
                            Log.warnf("Log config ignored because the level is unknown: %s", logConfig.level);
                            continue;
                        }

                        Logger logger = Logger.getLogger(logConfig.category);

                        Level currentLevel = logger.getLevel();
                        if (!newLevel.get().equals(currentLevel)) {
                            previousLogLevels.put(logConfig.category, currentLevel);
                            logger.setLevel(newLevel.get());
                            logUpdated(logConfig.category, currentLevel, newLevel.get());
                        }
                    }
                }

                Set<String> categories = Arrays.stream(logConfigs)
                        .filter(this::shouldCurrentHostBeUpdated)
                        .map(logConfig -> logConfig.category)
                        .collect(toSet());
                previousLogLevels.entrySet().removeIf(entry -> {
                    boolean remove = !categories.contains(entry.getKey());
                    if (remove) {
                        revertLogLevel(entry.getKey(), entry.getValue());
                    }
                    return remove;
                });

            } else {
                previousLogLevels.forEach(LogLevelManager::revertLogLevel);
                previousLogLevels.clear();
            }

        } catch (Exception e) {
            Log.errorf(e, "%s payload processing failed", UNLEASH_TOGGLE_NAME);
        }
    }

    private boolean shouldCurrentHostBeUpdated(LogConfig logConfig) {
        if (logConfig.hostName == null) {
            return true;
        }
        if (logConfig.hostName.endsWith("*")) {
            return hostName.startsWith(logConfig.hostName.substring(0, logConfig.hostName.length() - 1));
        } else {
            return hostName.equals(logConfig.hostName);
        }
    }

    /**
     * Converts a Quarkus log level into a JUL log level.
     * @param level the Quarkus log level
     * @return the JUL log level
     */
    private static Optional<Level> getLogLevel(String level) {
        return switch (level) {
            case "TRACE" -> Optional.of(FINER);
            case "DEBUG" -> Optional.of(FINE);
            case "INFO" -> Optional.of(INFO);
            case "WARN" -> Optional.of(WARNING);
            case "ERROR" -> Optional.of(SEVERE);
            default -> Optional.empty();
        };
    }

    private static void revertLogLevel(String category, Level previousLevel) {
        Logger logger = Logger.getLogger(category);
        Level currentLevel = logger.getLevel();
        logger.setLevel(previousLevel);
        logUpdated(category, currentLevel, previousLevel);
    }

    private static void logUpdated(String category, Level oldLevel, Level newLevel) {
        Log.infof("Log level updated [category=%s, oldLevel=%s, newLevel=%s]", category, toString(oldLevel), toString(newLevel));
    }

    private static String toString(Level level) {
        return level == null ? INHERITED : level.getName();
    }
}
