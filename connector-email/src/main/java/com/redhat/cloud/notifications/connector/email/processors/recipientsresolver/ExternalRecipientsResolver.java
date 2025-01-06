package com.redhat.cloud.notifications.connector.email.processors.recipientsresolver;

import com.redhat.cloud.notifications.connector.email.model.settings.RecipientSettings;
import com.redhat.cloud.notifications.connector.email.model.settings.User;
import com.redhat.cloud.notifications.connector.email.processors.recipients.RecipientsQuery;
import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import dev.failsafe.function.CheckedSupplier;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import java.io.IOException;
import java.time.Duration;
import java.util.Set;

@ApplicationScoped
public class ExternalRecipientsResolver {

    @Inject
    @RestClient
    RecipientsResolverService recipientsResolverService;

    public static final String NOTIFICATIONS_RECIPIENTS_RESOLVER_RETRY_MAX_ATTEMPTS = "notifications.recipients-resolver.retry.max-attempts";
    public static final String NOTIFICATIONS_RECIPIENTS_RESOLVER_RETRY_INITIAL_BACKOFF = "notifications.recipients-resolver.retry.initial-backoff";
    public static final String NOTIFICATIONS_RECIPIENTS_RESOLVER_RETRY_MAX_BACKOFF = "notifications.recipients-resolver.retry.max-backoff";

    @ConfigProperty(name = NOTIFICATIONS_RECIPIENTS_RESOLVER_RETRY_MAX_ATTEMPTS, defaultValue = "3")
    int maxRetryAttempts;

    @ConfigProperty(name = NOTIFICATIONS_RECIPIENTS_RESOLVER_RETRY_INITIAL_BACKOFF, defaultValue = "0.1S")
    Duration initialRetryBackoff;

    @ConfigProperty(name = NOTIFICATIONS_RECIPIENTS_RESOLVER_RETRY_MAX_BACKOFF, defaultValue = "1S")
    Duration maxRetryBackoff;

    private RetryPolicy<Object> retryPolicy;

    @PostConstruct
    public void postConstruct() {
        retryPolicy = RetryPolicy.builder()
            .handle(IOException.class)
            .withBackoff(initialRetryBackoff, maxRetryBackoff)
            .withMaxAttempts(maxRetryAttempts)
            .onRetriesExceeded(event ->
                // All retry attempts failed, let's log a warning about the failure.
                Log.warn("Users fetching from external service failed", event.getException())
            )
            .build();
    }

    private <T> T retryOnError(final CheckedSupplier<T> usersServiceCall) {
        return Failsafe.with(retryPolicy).get(usersServiceCall);
    }

    public Set<User> recipientUsers(String orgId, Set<RecipientSettings> recipientSettings, Set<String> subscribers, Set<String> unsubscribers, boolean subscribedByDefault, JsonObject recipientsAuthorizationCriterion) {
        RecipientsQuery recipientsQuery = new RecipientsQuery();
        recipientsQuery.subscribers = Set.copyOf(subscribers);
        recipientsQuery.unsubscribers = Set.copyOf(unsubscribers);
        recipientsQuery.orgId = orgId;
        Set<RecipientSettings> recipientSettingsSet = Set.copyOf(recipientSettings);

        recipientsQuery.recipientSettings = recipientSettingsSet;
        recipientsQuery.subscribedByDefault = subscribedByDefault;
        recipientsQuery.recipientsAuthorizationCriterion = recipientsAuthorizationCriterion;
        return retryOnError(() -> recipientsResolverService.getRecipients(recipientsQuery));
    }

}
