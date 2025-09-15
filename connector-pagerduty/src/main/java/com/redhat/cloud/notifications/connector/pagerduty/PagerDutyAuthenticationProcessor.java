package com.redhat.cloud.notifications.connector.pagerduty;

import com.redhat.cloud.notifications.connector.ExceptionProcessor;
import com.redhat.cloud.notifications.connector.authentication.AuthenticationType;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Processes authentication for PagerDuty connector.
 * PagerDuty uses routing keys for authentication in Events API v2.
 */
@ApplicationScoped
public class PagerDutyAuthenticationProcessor {

    public Uni<Void> processAuthentication(ExceptionProcessor.ProcessingContext context) {
        AuthenticationType authType = context.getAdditionalProperty("AUTHENTICATION_TYPE", AuthenticationType.class);

        if (authType != null) {
            String secretPassword = context.getAdditionalProperty("SECRET_PASSWORD", String.class);

            switch (authType) {
                case BEARER -> {
                    // For PagerDuty, Bearer tokens are not used for Events API
                    // The routing key is included in the payload
                    if (secretPassword != null) {
                        context.setAdditionalProperty("ROUTING_KEY", secretPassword);
                    }
                }
                case SECRET_TOKEN -> {
                    // PagerDuty routing key is typically stored as SECRET_TOKEN
                    if (secretPassword != null) {
                        context.setAdditionalProperty("ROUTING_KEY", secretPassword);
                    }
                }
                default -> {
                    throw new IllegalStateException("Unexpected authentication type: " + authType);
                }
            }
        }

        return Uni.createFrom().voidItem();
    }
}
