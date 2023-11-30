package com.redhat.cloud.notifications.connector.email;

import com.redhat.cloud.notifications.connector.EngineToConnectorRouteBuilder;
import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import com.redhat.cloud.notifications.connector.email.constants.Routes;
import com.redhat.cloud.notifications.connector.email.model.settings.User;
import com.redhat.cloud.notifications.connector.email.processors.bop.BOPRequestPreparer;
import com.redhat.cloud.notifications.connector.email.processors.recipients.RecipientsResolverPreparer;
import com.redhat.cloud.notifications.connector.email.processors.recipients.RecipientsResolverResponseProcessor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Predicate;
import org.apache.camel.builder.endpoint.dsl.HttpEndpointBuilderFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;

import java.util.HashSet;
import java.util.Set;

import static com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder.SUCCESS;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.ORG_ID;
import static com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty.EMAIL_RECIPIENTS;
import static com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty.FILTERED_USERS;
import static com.redhat.cloud.notifications.connector.http.SslTrustAllManager.getSslContextParameters;
import static org.apache.camel.LoggingLevel.INFO;

@ApplicationScoped
public class EmailRouteBuilder extends EngineToConnectorRouteBuilder {

    /**
     * Holds all the configuration parameters required to run the connector.
     */
    @Inject
    EmailConnectorConfig emailConnectorConfig;

    /**
     * Processor which prepares the request and the payload to be sent to BOP,
     * in order to trigger an email sending.
     */
    @Inject
    BOPRequestPreparer BOPRequestPreparer;

    @Inject
    RecipientsResolverPreparer recipientsResolverPreparer;

    @Inject
    RecipientsResolverResponseProcessor recipientsResolverResponseProcessor;

    /**
     * Configures the flow for this connector.
     * @throws Exception if the IT SSL route could not be correctly set up.
     */
    @Override
    public void configureRoutes() throws Exception {

        from(seda(ENGINE_TO_CONNECTOR))
            .routeId(emailConnectorConfig.getConnectorName())
            // Initialize the users hash set, where we will gather the
            // fetched users from the user providers.
            .process(exchange -> exchange.setProperty(ExchangeProperty.USERS, new HashSet<User>()))
            .to(direct(Routes.RESOLVE_USERS_WITH_RECIPIENTS_RESOLVER_CLOWDAPP))

            // Once the split has finished, we can send the exchange to the BOP
            // route.
            .to(direct(Routes.SEND_EMAIL_BOP));

        /*
         * Fetches the users from recipients resolver.
         */
        from(direct(Routes.RESOLVE_USERS_WITH_RECIPIENTS_RESOLVER_CLOWDAPP))
            .routeId(Routes.RESOLVE_USERS_WITH_RECIPIENTS_RESOLVER_CLOWDAPP)
            .process(recipientsResolverPreparer)
            .to(emailConnectorConfig.getRecipientsResolverServiceURL() + "/internal/recipients-resolver")
            .process(recipientsResolverResponseProcessor);

        /*
         * Prepares the payload accepted by BOP and sends the request to
         * the service.
         */
        final HttpEndpointBuilderFactory.HttpEndpointBuilder bopEndpoint = this.setUpBOPEndpoint();

        from(direct(Routes.SEND_EMAIL_BOP))
            .routeId(Routes.SEND_EMAIL_BOP)
            .choice()
                .when(shouldSkipEmail())
                    // TODO Lower this log level to DEBUG later.
                    .log(INFO, getClass().getName(), "Skipped Email notification because the recipients list was empty [orgId=${exchangeProperty." + ORG_ID + "}, historyId=${exchangeProperty." + ID + "}]")
                .otherwise()
                    // Clear all the headers that may come from the previous route.
                    .removeHeaders("*")
                    .process(this.BOPRequestPreparer)
                    .to(bopEndpoint)
                    .log(INFO, getClass().getName(), "Sent Email notification [orgId=${exchangeProperty." + ORG_ID + "}, historyId=${exchangeProperty." + ID + "}]")
            .end()
            .to(direct(SUCCESS));
    }

    private Predicate shouldSkipEmail() {
        return exchange -> exchange.getProperty(FILTERED_USERS, Set.class).isEmpty() &&
            exchange.getProperty(EMAIL_RECIPIENTS, Set.class).isEmpty();
    }

    /**
     * Creates the endpoint for the BOP service. It makes Apache Camel trust
     * BOP service's certificate.
     * @return the created endpoint.
     */
    protected HttpEndpointBuilderFactory.HttpEndpointBuilder setUpBOPEndpoint() {
        // Remove the schema from the url to avoid the
        // "ResolveEndpointFailedException", which complaints about specifying
        // the schema twice.
        final String fullURL = this.emailConnectorConfig.getBopURL();
        if (fullURL.startsWith("https")) {
            return https(fullURL.replace("https://", ""))
                .sslContextParameters(getSslContextParameters())
                .x509HostnameVerifier(NoopHostnameVerifier.INSTANCE);
        } else {
            return http(fullURL.replace("http://", ""));
        }
    }
}
