package com.redhat.cloud.notifications.connector.email.processors.bop;

import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import com.redhat.cloud.notifications.connector.email.model.bop.Email;
import com.redhat.cloud.notifications.connector.email.model.bop.SendEmailsRequest;
import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import dev.failsafe.function.CheckedRunnable;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class BOPManager {

    public static final String NOTIFICATIONS_BOP_RETRY_MAX_ATTEMPTS = "notifications.bop.retry.max-attempts";
    public static final String NOTIFICATIONS_BOP_RETRY_INITIAL_BACKOFF = "notifications.bop.retry.initial-backoff";
    public static final String NOTIFICATIONS_BOP_RETRY_MAX_BACKOFF = "notifications.bop.retry.max-backoff";

    @ConfigProperty(name = NOTIFICATIONS_BOP_RETRY_MAX_ATTEMPTS, defaultValue = "3")
    int maxRetryAttempts;

    @ConfigProperty(name = NOTIFICATIONS_BOP_RETRY_INITIAL_BACKOFF, defaultValue = "0.1S")
    Duration initialRetryBackoff;

    @ConfigProperty(name = NOTIFICATIONS_BOP_RETRY_MAX_BACKOFF, defaultValue = "1S")
    Duration maxRetryBackoff;

    private RetryPolicy<Object> retryPolicy;

    @Inject
    @RestClient
    BOPService bopService;

    @Inject
    EmailConnectorConfig emailConnectorConfig;

    @PostConstruct
    public void postConstruct() {
        retryPolicy = RetryPolicy.builder()
            .handle(IOException.class, ProcessingException.class)
            .withBackoff(initialRetryBackoff, maxRetryBackoff)
            .withMaxAttempts(maxRetryAttempts)
            .onRetriesExceeded(event ->
                // All retry attempts failed, let's log a warning about the failure.
                Log.warn("Sending email to BOP service failed", event.getException())
            )
            .build();
    }

    private void retryOnError(final CheckedRunnable usersServiceCall) {
        Failsafe.with(retryPolicy).run(usersServiceCall);
    }

    public void sendToBop(List<String> recipients, String subject, String body, String sender) {

        // Prepare the email to be sent
        final Email email = new Email(
            subject,
            body,
            Set.copyOf(recipients)
        );

        final SendEmailsRequest request = new SendEmailsRequest(
            Set.of(email),
            sender,
            sender
        );

        retryOnError(() ->
            bopService.sendEmail(emailConnectorConfig.getBopApiToken(),
                emailConnectorConfig.getBopClientId(),
                emailConnectorConfig.getBopEnv(),
                request)
        );
    }
}
