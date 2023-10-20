package com.redhat.cloud.notifications.connector.drawer.recipients;

import com.redhat.cloud.notifications.connector.drawer.config.DrawerConnectorConfig;
import com.redhat.cloud.notifications.connector.drawer.constant.ExchangeProperty;
import com.redhat.cloud.notifications.connector.drawer.model.DrawerUser;
import com.redhat.cloud.notifications.connector.drawer.model.RecipientSettings;
import com.redhat.cloud.notifications.connector.drawer.recipients.pojo.RecipientsQuery;
import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import dev.failsafe.function.CheckedSupplier;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.ORG_ID;

@ApplicationScoped
public class RecipientsResolver implements Processor {

    @Inject
    @RestClient
    RecipientsResolverService recipientsResolverService;

    @Inject
    DrawerConnectorConfig drawerConnectorConfig;
    private RetryPolicy<Object> retryPolicy;

    @PostConstruct
    public void postConstruct() {
        retryPolicy = RetryPolicy.builder()
            .handle(IOException.class)
            .withBackoff(drawerConnectorConfig.getInitialRetryBackoff(), drawerConnectorConfig.getMaxRetryBackoff())
            .withMaxAttempts(drawerConnectorConfig.getMaxRetryAttempts())
            .onRetriesExceeded(event -> {
                // All retry attempts failed, let's log a warning about the failure.
                Log.warn("Users fetching from external service failed", event.getException());
            })
            .build();
    }

    private <T> T retryOnError(final CheckedSupplier<T> usersServiceCall) {
        return Failsafe.with(retryPolicy).get(usersServiceCall);
    }

    @Override
    public void process(final Exchange exchange) {
        List<RecipientSettings> recipientSettings = (List<RecipientSettings>) exchange.getProperty(ExchangeProperty.RECIPIENT_SETTINGS);
        List<String> unsubscribers = exchange.getProperty(ExchangeProperty.UNSUBSCRIBERS, List.class);
        final String orgId = (String) exchange.getProperty(ORG_ID);

        RecipientsQuery recipientsResolversQuery = new RecipientsQuery();
        recipientsResolversQuery.setSubscribers(Set.copyOf(unsubscribers));
        recipientsResolversQuery.setOrgId(orgId);
        recipientsResolversQuery.setRecipientSettings(Set.copyOf(recipientSettings));
        recipientsResolversQuery.setOptIn(false);
        List<DrawerUser> recipientsList = retryOnError(() -> recipientsResolverService.getRecipients(recipientsResolversQuery));
        exchange.setProperty(ExchangeProperty.RESOLVED_RECIPIENT_LIST, recipientsList);
    }
}
