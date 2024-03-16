package com.redhat.cloud.notifications.config;

import com.redhat.cloud.notifications.unleash.ToggleChangedLogger;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;

import static com.redhat.cloud.notifications.config.EngineConfig.AGGREGATION_WITH_RECIPIENTS_RESOLVER;
import static com.redhat.cloud.notifications.config.EngineConfig.ASYNC_AGGREGATION;
import static com.redhat.cloud.notifications.config.EngineConfig.DRAWER;
import static com.redhat.cloud.notifications.config.EngineConfig.HCC_EMAIL_SENDER_NAME;
import static com.redhat.cloud.notifications.config.EngineConfig.KAFKA_CONSUMED_TOTAL_CHECKER;

@ApplicationScoped
@Unremovable
public class EngineToggleChangedLogger extends ToggleChangedLogger {

    private static final Set<String> LOGGED_TOGGLES = Set.of(
        AGGREGATION_WITH_RECIPIENTS_RESOLVER,
        ASYNC_AGGREGATION,
        DRAWER,
        HCC_EMAIL_SENDER_NAME,
        KAFKA_CONSUMED_TOTAL_CHECKER
    );

    @Override
    protected Set<String> getLoggedToggles() {
        return LOGGED_TOGGLES;
    }
}
