package com.redhat.cloud.notifications.connector.email;

import com.redhat.cloud.notifications.connector.EngineToConnectorRouteBuilder;
import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import com.redhat.cloud.notifications.connector.email.constants.Routes;
import com.redhat.cloud.notifications.connector.email.metrics.EmailMetricsProcessor;
import com.redhat.cloud.notifications.connector.email.processors.bop.BOPRequestPreparer;
import com.redhat.cloud.notifications.connector.email.processors.recipients.RecipientsResolverRequestPreparer;
import com.redhat.cloud.notifications.connector.email.processors.recipients.RecipientsResolverResponseProcessor;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Predicate;
import org.apache.camel.builder.endpoint.dsl.HttpEndpointBuilderFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;

import java.util.Set;

import static com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder.SUCCESS;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.ORG_ID;
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
    RecipientsResolverRequestPreparer recipientsResolverRequestPreparer;

    @Inject
    RecipientsResolverResponseProcessor recipientsResolverResponseProcessor;

    @Inject
    EmailMetricsProcessor emailMetricsProcessor;

    /**
     * Configures the flow for this connector.
     */
    @Override
    public void configureRoutes() {

        from(seda(ENGINE_TO_CONNECTOR))
            .routeId(emailConnectorConfig.getConnectorName())
            .process(recipientsResolverRequestPreparer)
            .to(createRecipientResolverEndpoint())
            .process(recipientsResolverResponseProcessor)
            .choice().when(shouldSkipEmail())
                .log(INFO, getClass().getName(), "Skipped Email notification because the recipients list was empty [orgId=${exchangeProperty." + ORG_ID + "}, historyId=${exchangeProperty." + ID + "}]")
            .otherwise()
                .to(direct(Routes.SPLIT_AND_SEND))
            .end()
            .to(direct(SUCCESS));

        from(direct(Routes.SPLIT_AND_SEND))
            .routeId(Routes.SPLIT_AND_SEND)
            .split(simpleF("${exchangeProperty.%s}", ExchangeProperty.FILTERED_USERS))
                .to(direct(Routes.SEND_EMAIL_BOP))
            .end();

        /*
         * Attempt setting up a secured BOP endpoint. If any error occurs
         * during the creation of the secure endpoint, we fall back to an
         * insecure one.
         */
        try {
            final HttpEndpointBuilderFactory.HttpEndpointBuilder endpoint = this.setUpSecuredBOPEndpoint();

            from(direct(Routes.SEND_EMAIL_BOP))
                .routeId(Routes.SEND_EMAIL_BOP)
                // Clear all the headers that may come from the previous route.
                .removeHeaders("*")
                .process(this.BOPRequestPreparer)
                .to(endpoint)
                .log(INFO, getClass().getName(), "Sent Email notification [orgId=${exchangeProperty." + ORG_ID + "}, historyId=${exchangeProperty." + ID + "}]")
                .process(emailMetricsProcessor);
        } catch (final Exception e) {
            Log.info("Unable to set up a secured BOP endpoint. Setting an insecure one instead", e);

            final HttpEndpointBuilderFactory.HttpEndpointBuilder bopEndpoint = this.setUpBOPEndpoint();

            from(direct(Routes.SEND_EMAIL_BOP))
                .routeId(Routes.SEND_EMAIL_BOP)
                // Clear all the headers that may come from the previous route.
                .removeHeaders("*")
                .process(this.BOPRequestPreparer)
                .to(bopEndpoint)
                .log(INFO, getClass().getName(), "Sent Email notification [orgId=${exchangeProperty." + ORG_ID + "}, historyId=${exchangeProperty." + ID + "}]")
                .process(emailMetricsProcessor);
        }
    }

    private Predicate shouldSkipEmail() {
        return exchange -> exchange.getProperty(FILTERED_USERS, Set.class).isEmpty();
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

    /**
     * Creates the endpoint for the BOP service. It makes Apache Camel trust
     * the service's certificate.
     * @return the created endpoint.
     */
    private HttpEndpointBuilderFactory.HttpEndpointBuilder setUpSecuredBOPEndpoint() {
        /*final KeyStoreParameters ksp = new KeyStoreParameters();
        ksp.setResource(this.emailConnectorConfig.getBopKeyStoreLocation());
        ksp.setPassword(this.emailConnectorConfig.getBopKeyStorePassword());

        final TrustManagersParameters tmp = new TrustManagersParameters();
        tmp.setKeyStore(ksp);

        final SSLContextParameters scp = new SSLContextParameters();
        scp.setTrustManagers(tmp);*/

        // TODO Uncomment below if the call to recipients-resolver fails with an SslHandshakeException
        //final HttpComponent httpComponent = this.getCamelContext().getComponent("https", HttpComponent.class);
        //httpComponent.setSslContextParameters(scp);

        final String fullURL = this.emailConnectorConfig.getBopURL();
        if (fullURL.startsWith("https")) {
            return https(fullURL.replace("https://", ""));
                    //.sslContextParameters(scp);
        } else {
            return http(fullURL.replace("http://", ""));
        }
    }

    private HttpEndpointBuilderFactory.HttpEndpointBuilder createRecipientResolverEndpoint() {
        final String fullURL = emailConnectorConfig.getRecipientsResolverServiceURL() + "/internal/recipients-resolver";
        if (fullURL.startsWith("https")) {
            return https(fullURL.replace("https://", ""))
                    .sslContextParameters(getSslContextParameters())
                    .x509HostnameVerifier(NoopHostnameVerifier.INSTANCE);
        } else {
            return http(fullURL.replace("http://", ""));
        }
    }
}
