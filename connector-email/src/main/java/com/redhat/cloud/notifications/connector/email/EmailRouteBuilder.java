package com.redhat.cloud.notifications.connector.email;

import com.redhat.cloud.notifications.connector.EngineToConnectorRouteBuilder;
import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import com.redhat.cloud.notifications.connector.email.constants.Routes;
import com.redhat.cloud.notifications.connector.email.metrics.EmailMetricsProcessor;
import com.redhat.cloud.notifications.connector.email.processors.bop.BOPRequestPreparer;
import com.redhat.cloud.notifications.connector.email.processors.recipients.RecipientsResolverRequestPreparer;
import com.redhat.cloud.notifications.connector.email.processors.recipients.RecipientsResolverResponseProcessor;
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

    static final String BOP_RESPONSE_TIME_METRIC = "micrometer:timer:email.bop.response.time";
    static final String BOP_V2_RESPONSE_TIME_METRIC = "micrometer:timer:email.bop.v2.response.time";

    static final String RECIPIENTS_RESOLVER_RESPONSE_TIME_METRIC = "micrometer:timer:email.recipients_resolver.response.time";
    static final String TIMER_ACTION_START = "?action=start";
    static final String TIMER_ACTION_STOP = "?action=stop";

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

        /*
         * Prepares the payload accepted by BOP and sends the request to
         * the service.
         */
        final HttpEndpointBuilderFactory.HttpEndpointBuilder bopEndpointV1 = setUpBOPEndpointV1();
        final HttpEndpointBuilderFactory.HttpEndpointBuilder bopEndpointV2 = setUpBOPEndpointV2();

        from(seda(ENGINE_TO_CONNECTOR))
            .routeId(emailConnectorConfig.getConnectorName())
            .process(recipientsResolverRequestPreparer)
            .to(RECIPIENTS_RESOLVER_RESPONSE_TIME_METRIC + TIMER_ACTION_START)
                .to(setupRecipientResolverEndpoint())
            .to(RECIPIENTS_RESOLVER_RESPONSE_TIME_METRIC + TIMER_ACTION_START)
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

        from(direct(Routes.SEND_EMAIL_BOP))
            .routeId(Routes.SEND_EMAIL_BOP)
            // Clear all the headers that may come from the previous route.
            .removeHeaders("*")
            .process(this.BOPRequestPreparer)
            .choice().when(shouldUseBopEmailServiceV2())
                .to(BOP_V2_RESPONSE_TIME_METRIC + TIMER_ACTION_START)
                    .to(bopEndpointV2)
                .to(BOP_V2_RESPONSE_TIME_METRIC + TIMER_ACTION_STOP)
            .otherwise()
                .to(BOP_RESPONSE_TIME_METRIC + TIMER_ACTION_START)
                    .to(bopEndpointV1)
                .to(BOP_RESPONSE_TIME_METRIC + TIMER_ACTION_STOP)
            .end()
            .log(INFO, getClass().getName(), "Sent Email notification [orgId=${exchangeProperty." + ORG_ID + "}, historyId=${exchangeProperty." + ID + "}]")
            .process(emailMetricsProcessor);
    }

    private Predicate shouldSkipEmail() {
        return exchange -> exchange.getProperty(FILTERED_USERS, Set.class).isEmpty();
    }

    private Predicate shouldUseBopEmailServiceV2() {
        // TODO update it before prod to be able to filter on org_id
        return exchange -> emailConnectorConfig.isEnableBopServiceV2Usage();
    }

    /**
     * Creates the endpoint for the BOP service. It makes Apache Camel trust
     * BOP service's certificate.
     * @return the created endpoint.
     */
    protected HttpEndpointBuilderFactory.HttpEndpointBuilder setUpBOPEndpointV1() {
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

    protected HttpEndpointBuilderFactory.HttpEndpointBuilder setUpBOPEndpointV2() {
        // Remove the schema from the url to avoid the
        // "ResolveEndpointFailedException", which complaints about specifying
        // the schema twice.
        final String fullURL = this.emailConnectorConfig.getBopURL();
        if (fullURL.startsWith("https")) {
            return https(fullURL.replace("https://", ""));
        } else {
            return http(fullURL.replace("http://", ""));
        }
    }

    private HttpEndpointBuilderFactory.HttpEndpointBuilder setupRecipientResolverEndpoint() {
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
