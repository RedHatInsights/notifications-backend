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
import org.apache.camel.builder.endpoint.dsl.KafkaEndpointBuilderFactory;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.apache.http.conn.ssl.NoopHostnameVerifier;

import java.util.Set;

import static com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder.SUCCESS;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.ORG_ID;
import static com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty.FILTERED_USERS;
import static com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty.USE_EMAIL_BOP_V1_SSL;
import static com.redhat.cloud.notifications.connector.http.SslTrustAllManager.getSslContextParameters;
import static org.apache.camel.LoggingLevel.DEBUG;
import static org.apache.camel.LoggingLevel.INFO;
import static org.apache.camel.builder.endpoint.dsl.HttpEndpointBuilderFactory.HttpEndpointBuilder;

@ApplicationScoped
public class EmailRouteBuilder extends EngineToConnectorRouteBuilder {

    static final String BOP_RESPONSE_TIME_METRIC = "micrometer:timer:email.bop.response.time";
    static final String RECIPIENTS_RESOLVER_RESPONSE_TIME_METRIC = "micrometer:timer:email.recipients_resolver.response.time";
    static final String ROUTE_ID_KAFKA_HIGH_VOLUME_ROUTE = "kafka-high-volume-entrypoint";
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
        // Read events from the high volume topic and forward them to the
        // entrypoint, so that they get processed as usual.
        if (this.emailConnectorConfig.isIncomingKafkaHighVolumeTopicEnabled()) {
            from(this.buildKafkaHighVolumeEndpoint())
                .routeId(ROUTE_ID_KAFKA_HIGH_VOLUME_ROUTE)
                .to(direct(ENTRYPOINT));
        }

        /*
         * Prepares the payload accepted by BOP and sends the request to
         * the service.
         */
        final HttpEndpointBuilder bopEndpointV1 = setUpBOPEndpointV1();

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
            .choice().when(shouldUseBopEmailServiceWithSslChecks())
                .log(DEBUG, getClass().getName(), "Sent Email notification [orgId=${exchangeProperty." + ORG_ID + "}, historyId=${exchangeProperty." + ID + "} using regular SSL checks on email service]")
                .to(BOP_RESPONSE_TIME_METRIC + TIMER_ACTION_START)
                    .to(emailConnectorConfig.getBopURL())
                .to(BOP_RESPONSE_TIME_METRIC + TIMER_ACTION_STOP)
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

    private Predicate shouldUseBopEmailServiceWithSslChecks() {
        return exchange -> exchange.getProperty(USE_EMAIL_BOP_V1_SSL, Boolean.class);
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

    private HttpEndpointBuilder setupRecipientResolverEndpoint() {
        final String fullURL = emailConnectorConfig.getRecipientsResolverServiceURL() + "/internal/recipients-resolver";

        if (fullURL.startsWith("https")) {
            HttpEndpointBuilder endpointBuilder = https(fullURL.replace("https://", ""));
            if (emailConnectorConfig.getRecipientsResolverTrustStorePath().isPresent() && emailConnectorConfig.getRecipientsResolverTrustStorePassword().isPresent() && emailConnectorConfig.getRecipientsResolverTrustStoreType().isPresent()) {

                KeyStoreParameters keyStoreParameters = new KeyStoreParameters();
                keyStoreParameters.setResource("file:" + emailConnectorConfig.getRecipientsResolverTrustStorePath().get());
                keyStoreParameters.setPassword(emailConnectorConfig.getRecipientsResolverTrustStorePassword().get());
                keyStoreParameters.setType(emailConnectorConfig.getRecipientsResolverTrustStoreType().get());

                TrustManagersParameters trustManagersParameters = new TrustManagersParameters();
                trustManagersParameters.setKeyStore(keyStoreParameters);

                SSLContextParameters sslContextParameters = new SSLContextParameters();
                sslContextParameters.setTrustManagers(trustManagersParameters);

                endpointBuilder.sslContextParameters(sslContextParameters);
            } else {
                Log.warn("TLS is enabled for recipients-resolver but the trust store could not be used to build the Camel endpoint because the trust store path, password or type are missing in the Clowder configuration");
            }
            return endpointBuilder;
        } else {
            return http(fullURL.replace("http://", ""));
        }
    }

    /**
     * Builds the Kafka consumer for the high volume Kafka topic.
     * @return the built endpoint for the high volume Kafka consumer.
     */
    private KafkaEndpointBuilderFactory.KafkaEndpointConsumerBuilder buildKafkaHighVolumeEndpoint() {
        return kafka(this.emailConnectorConfig.getIncomingKafkaHighVolumeTopic())
            .groupId(this.emailConnectorConfig.getIncomingKafkaGroupId())
            .maxPollRecords(this.emailConnectorConfig.getIncomingKafkaHighVolumeMaxPollRecords())
            .maxPollIntervalMs(this.emailConnectorConfig.getIncomingKafkaHighVolumeMaxPollIntervalMs())
            .pollOnError(this.emailConnectorConfig.getIncomingKafkaHighVolumePollOnError());
    }
}
