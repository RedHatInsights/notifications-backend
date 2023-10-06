package com.redhat.cloud.notifications.connector.email;

import com.redhat.cloud.notifications.connector.ConnectorConfig;
import com.redhat.cloud.notifications.connector.EngineToConnectorRouteBuilder;
import com.redhat.cloud.notifications.connector.email.aggregation.UserAggregationStrategy;
import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import com.redhat.cloud.notifications.connector.email.constants.Routes;
import com.redhat.cloud.notifications.connector.email.predicates.NotFinishedFetchingAllPages;
import com.redhat.cloud.notifications.connector.email.predicates.rbac.StatusCodeNotFound;
import com.redhat.cloud.notifications.connector.email.processors.bop.BOPRequestPreparer;
import com.redhat.cloud.notifications.connector.email.processors.bop.ssl.BOPTrustManager;
import com.redhat.cloud.notifications.connector.email.processors.dispatcher.DispatcherProcessor;
import com.redhat.cloud.notifications.connector.email.processors.it.ITResponseProcessor;
import com.redhat.cloud.notifications.connector.email.processors.it.ITUserRequestPreparer;
import com.redhat.cloud.notifications.connector.email.processors.rbac.RBACConstants;
import com.redhat.cloud.notifications.connector.email.processors.rbac.RBACPrincipalsRequestPreparerProcessor;
import com.redhat.cloud.notifications.connector.email.processors.rbac.RBACUsersProcessor;
import com.redhat.cloud.notifications.connector.email.processors.rbac.group.RBACGroupPrincipalsRequestPreparerProcessor;
import com.redhat.cloud.notifications.connector.email.processors.rbac.group.RBACGroupProcessor;
import com.redhat.cloud.notifications.connector.email.processors.rbac.group.RBACGroupRequestPreparerProcessor;
import com.redhat.cloud.notifications.connector.email.processors.recipients.RecipientsFilter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Expression;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.builder.endpoint.dsl.HttpEndpointBuilderFactory;
import org.apache.camel.component.caffeine.CaffeineConfiguration;
import org.apache.camel.component.caffeine.CaffeineConstants;
import org.apache.camel.component.caffeine.EvictionType;
import org.apache.camel.component.caffeine.cache.CaffeineCacheComponent;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.apache.http.conn.ssl.NoopHostnameVerifier;

import java.util.HashSet;

import static com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder.SUCCESS;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.ORG_ID;
import static org.apache.camel.LoggingLevel.INFO;

@ApplicationScoped
public class EmailRouteBuilder extends EngineToConnectorRouteBuilder {
    @Inject
    ConnectorConfig connectorConfig;

    /**
     * Dispatches the exchange either to fetch the RBAC group or directly to
     * fetch the users, depending on whether the group is present or not in
     * the incoming exchange.
     */
    @Inject
    DispatcherProcessor dispatcherProcessor;

    /**
     * Holds all the configuration parameters required to run the connector.
     */
    @Inject
    EmailConnectorConfig emailConnectorConfig;

    /**
     * Processor which takes the response from BOP and transforms it to our
     * internal users model.
     */
    @Inject
    ITResponseProcessor itResponseProcessor;

    /**
     * Processor which builds the payload that BOP expects.
     */
    @Inject
    ITUserRequestPreparer itUserRequestPreparer;

    /**
     * Processor which prepares the request and the payload to be sent to BOP,
     * in order to trigger an email sending.
     */
    @Inject
    BOPRequestPreparer BOPRequestPreparer;

    /**
     * Predicate which decides if we should keep looping for more user pages.
     */
    @Inject
    NotFinishedFetchingAllPages notFinishedFetchingAllPages;

    /**
     * Processor which prepares the exchange and the request settings before
     * sending it to fetch all the principals from RBAC.
     */
    @Inject
    RBACPrincipalsRequestPreparerProcessor rbacPrincipalsRequestPreparerProcessor;

    /**
     * Processor which prepares the exchange and the request before sending it
     * to fetch the principals from an RBAC group.
     */
    @Inject
    RBACGroupPrincipalsRequestPreparerProcessor rbacGroupPrincipalsRequestPreparerProcessor;

    /**
     * Processor which prepares the exchange and the request before sending
     * the "get principals from group" request.
     */
    @Inject
    RBACGroupRequestPreparerProcessor rbacGroupRequestPreparerProcessor;

    /**
     * Processor which transforms the RBAC response into our internal users
     * model.
     */
    @Inject
    RBACUsersProcessor rbacUsersProcessor;

    /**
     * Processor which filters the users that should not be notified for a
     * given event.
     */
    @Inject
    RecipientsFilter recipientsFilter;

    /**
     * Processor which extracts the "is the group platform default?" key from
     * the received payload, to determine whether it is needed to fetch the
     * principals from that group or just all the principals from the tenant.
     */
    @Inject
    RBACGroupProcessor rbacGroupProcessor;

    /**
     * Predicate which asserts if the
     */
    @Inject
    StatusCodeNotFound statusCodeNotFound;

    /**
     * Aggregates the users received from the user providers.
     */
    @Inject
    UserAggregationStrategy userAggregationStrategy;

    /**
     * Configures the flow for this connector.
     * @throws Exception if the IT SSL route could not be correctly set up.
     */
    @Override
    public void configureRoute() throws Exception {
        /*
         * Configure Caffeine cache.
         */
        this.configureCaffeineCache();

        from(direct(ENGINE_TO_CONNECTOR))
            .routeId(this.connectorConfig.getConnectorName())
            // Split each recipient setting and aggregate the usernames to end
            // up with a single exchange.
            .split(simpleF("${exchangeProperty.%s}", ExchangeProperty.RECIPIENT_SETTINGS), this.userAggregationStrategy).stopOnException()
                // Initialize the usernames hash set, where we will gather the
                // fetched users from the user providers.
                .process(exchange -> exchange.setProperty(ExchangeProperty.USERNAMES, new HashSet<String>()))
                // As the body of the exchange might change throughout the
                // routes, save it in an exchange property.
                .setProperty(ExchangeProperty.CURRENT_RECIPIENT_SETTINGS, body())
                .choice()
                    .when(simpleF("${exchangeProperty.%s.groupUUID} == null", ExchangeProperty.CURRENT_RECIPIENT_SETTINGS))
                        .to(direct(Routes.FETCH_USERS))
                    .otherwise()
                        .to(direct(Routes.FETCH_GROUP))
                .end()
            .end()
            // Once the split has finished, we can send the exchange to the BOP
            // route.
            .to(direct(Routes.SEND_EMAIL_BOP_CHOICE));

        /*
         * Decides whether we should use RBAC or IT to fetch the users.
         */
        from(direct(Routes.FETCH_USERS))
            .routeId(Routes.FETCH_USERS)
            .choice()
                .when(PredicateBuilder.constant(this.emailConnectorConfig.isFetchUsersWithRBAC()))
                    .to(direct(Routes.FETCH_USERS_RBAC))
                .otherwise()
                    .to(direct(Routes.FETCH_USERS_IT))
            .endChoice();

        /*
         * Fetches the users from RBAC and filters them.
         */
        from(direct(Routes.FETCH_USERS_RBAC))
            .routeId(Routes.FETCH_USERS_RBAC)
            .setHeader(CaffeineConstants.ACTION, constant(CaffeineConstants.ACTION_GET))
            .setHeader(CaffeineConstants.KEY, this.computeCacheKey())
            .to(caffeineCache(Routes.FETCH_USERS_RBAC))
            // Avoid calling RBAC if we do have the usernames cached.
            .choice()
                .when(header(CaffeineConstants.ACTION_HAS_RESULT).isEqualTo(Boolean.TRUE))
                    // The cache engine leaves the usernames in the body of the
                    // exchange, that is why we need to set them back in the
                    // property that the subsequent processors expect to find them.
                    .setProperty(ExchangeProperty.USERNAMES, body())
                .otherwise()
                    // Clear all the headers that may come from the previous route.
                    .removeHeaders("*")
                    // Initialize the offset and the loop condition so that we at least
                    // enter once on it.
                    .setProperty(ExchangeProperty.OFFSET, constant(0))
                    .setProperty(ExchangeProperty.LIMIT, constant(this.emailConnectorConfig.getRbacElementsPerPage()))
                    // Begin fetching the users...
                    .loopDoWhile(this.notFinishedFetchingAllPages)
                        // Prepare the request and its headers.
                        .process(this.rbacPrincipalsRequestPreparerProcessor)
                        .to(this.emailConnectorConfig.getRbacURL())
                        // Process the results and determine if we should keep looping.
                        .process(this.rbacUsersProcessor)
                    .end()
                    // Store all the received recipients in the cache.
                    .setHeader(CaffeineConstants.ACTION, constant(CaffeineConstants.ACTION_PUT))
                    .setHeader(CaffeineConstants.KEY, this.computeCacheKey())
                    .setHeader(CaffeineConstants.VALUE, exchangeProperty(ExchangeProperty.USERNAMES))
                    .to(caffeineCache(Routes.FETCH_USERS_RBAC))
            .endChoice()
            .end()
            .process(this.recipientsFilter);

        /*
         * Fetches the users from IT and filters them. The IT's endpoint is set
         * up separately because we need to create a special SSL context which
         * trusts IT's certificate.
         */
        final HttpEndpointBuilderFactory.HttpEndpointBuilder itEndpoint = this.setUpITEndpoint();

        from(direct(Routes.FETCH_USERS_IT))
            .routeId(Routes.FETCH_USERS_IT)
            .setHeader(CaffeineConstants.ACTION, constant(CaffeineConstants.ACTION_GET))
            .setHeader(CaffeineConstants.KEY, this.computeCacheKey())
            .to(caffeineCache(Routes.FETCH_USERS_IT))
            // Avoid calling IT if we do have the usernames cached.
            .choice()
                .when(header(CaffeineConstants.ACTION_HAS_RESULT).isEqualTo(Boolean.TRUE))
                    // The cache engine leaves the usernames in the body of the
                    // exchange, that is why we need to set them back in the
                    // property that the subsequent processors expect to find them.
                    .setProperty(ExchangeProperty.USERNAMES, body())
                .otherwise()
                    // Clear all the headers that may come from the previous route.
                    .removeHeaders("*")
                    // Initialize the offset and the loop condition so that we at least
                    // enter once on it.
                    .setProperty(ExchangeProperty.OFFSET, constant(0))
                    .setProperty(ExchangeProperty.LIMIT, constant(this.emailConnectorConfig.getItElementsPerPage()))
                    // Begin fetching the users...
                    .loopDoWhile(this.notFinishedFetchingAllPages)
                        .process(this.itUserRequestPreparer)
                        .to(itEndpoint)
                        .process(this.itResponseProcessor)
                    .end()
                    // Store all the received recipients in the cache.
                    .setHeader(CaffeineConstants.ACTION, constant(CaffeineConstants.ACTION_PUT))
                    .setHeader(CaffeineConstants.KEY, this.computeCacheKey())
                    .setHeader(CaffeineConstants.VALUE, exchangeProperty(ExchangeProperty.USERNAMES))
                    .to(caffeineCache(Routes.FETCH_USERS_IT))
            .endChoice()
            .end()
            .process(this.recipientsFilter);

        /*
         * Fetches an RBAC group. If it doesn't exist for some reason, we
         * simply fetch all the users as normal. If it exists, we just fetch
         * the users from that particular group.
         */
        from(direct(Routes.FETCH_GROUP))
            .routeId(Routes.FETCH_GROUP)
            // Clear all the headers that may come from the previous route.
            .removeHeaders("*")
            .process(this.rbacGroupRequestPreparerProcessor)
            .to(this.emailConnectorConfig.getRbacURL())
            .process(this.rbacGroupProcessor)
            .choice()
                .when(simpleF("${exchangeProperty.%s}", RBACConstants.RBAC_GROUP_IS_PLATFORM_DEFAULT))
                    .to(direct(Routes.FETCH_USERS))
                .otherwise()
                    .to(direct(Routes.FETCH_GROUP_USERS))
            .end()
            // When a 404 "Not Found" status code is received, then we simply
            // continue with the execution.
            .onException(HttpOperationFailedException.class)
                .onWhen(this.statusCodeNotFound)
                    .handled(true)
                    .process(this.recipientsFilter);

        /*
         * Fetch the users from an RBAC group and filters the results according
         * to the recipient settings.
         */
        from(direct(Routes.FETCH_GROUP_USERS))
            .routeId(Routes.FETCH_GROUP_USERS)
            // Clear all the headers that may come from the previous route.
            .removeHeaders("*")
            // Initialize the offset and the loop condition so that we at least
            // enter once on it.
            .setProperty(ExchangeProperty.LIMIT, constant(this.emailConnectorConfig.getRbacElementsPerPage()))
            .setProperty(ExchangeProperty.OFFSET, constant(0))
            .loopDoWhile(this.notFinishedFetchingAllPages)
                .process(this.rbacGroupPrincipalsRequestPreparerProcessor)
                .to(this.emailConnectorConfig.getRbacURL())
                .process(this.rbacUsersProcessor)
            .end()
            .process(this.recipientsFilter);

        from(direct(Routes.SEND_EMAIL_BOP_CHOICE))
            .choice()
                .when(constant(this.emailConnectorConfig.isSingleEmailPerUserEnabled()))
                    .to(direct(Routes.SEND_EMAIL_BOP_SINGLE_PER_USER))
                .otherwise()
                    .to(direct(Routes.SEND_EMAIL_BOP))
            .end();

        /*
         * Prepares the payload accepted by BOP and sends the request to
         * the service.
         */
        final HttpEndpointBuilderFactory.HttpEndpointBuilder bopEndpoint = this.setUpBOPEndpoint();

        from(direct(Routes.SEND_EMAIL_BOP))
            .routeId(Routes.SEND_EMAIL_BOP)
            // Clear all the headers that may come from the previous route.
            .removeHeaders("*")
            .process(this.BOPRequestPreparer)
            .to(bopEndpoint)
            .log(INFO, getClass().getName(), "Sent Email notification [orgId=${exchangeProperty." + ORG_ID + "}, historyId=${exchangeProperty." + ID + "}]")
            .to(direct(SUCCESS));

        /*
         * Temporary route in order to be able to send an email per user,
         * instead of one per multiple users.
         */
        from(direct(Routes.SEND_EMAIL_BOP_SINGLE_PER_USER))
            .routeId(Routes.SEND_EMAIL_BOP_SINGLE_PER_USER)
            // Clear all the headers that may come from the previous route.
            .removeHeaders("*")
            .split(simpleF("${exchangeProperty.%s}", ExchangeProperty.FILTERED_USERNAMES))
                .setProperty(ExchangeProperty.SINGLE_EMAIL_PER_USER, constant(true))
                .to(direct(Routes.SEND_EMAIL_BOP))
            .end();
    }

    /**
     * Computes the cache key.
     * @return a simple expression with the "${orgId}-adminsOnly=${adminsOnly}"
     * format.
     */
    protected Expression computeCacheKey() {
        return simpleF(
            "${exchangeProperty.%s}-adminsOnly=${exchangeProperty.%s.adminsOnly}",
            ORG_ID,
            ExchangeProperty.CURRENT_RECIPIENT_SETTINGS
        );
    }

    /**
     * Sets a common configuration for all the Caffeine caches.
     */
    protected void configureCaffeineCache() {
        final CaffeineCacheComponent caffeineCacheComponent = this.getCamelContext().getComponent("caffeine-cache", CaffeineCacheComponent.class);
        final CaffeineConfiguration caffeineConfiguration = caffeineCacheComponent.getConfiguration();

        // We explicitly set the eviction type as time based, since the default
        // value is size based: https://camel.apache.org/components/4.0.x/caffeine-cache-component.html.
        caffeineConfiguration.setEvictionType(EvictionType.TIME_BASED);
        caffeineConfiguration.setExpireAfterWriteTime(emailConnectorConfig.getUserProviderCacheExpireAfterWrite());
        // This shouldn't be needed but Camel's CaffeineConfiguration makes this mandatory...
        caffeineConfiguration.setExpireAfterAccessTime(emailConnectorConfig.getUserProviderCacheExpireAfterWrite());
    }

    /**
     * Creates the endpoint for the IT Users service. It makes Apache Camel
     * trust the service's certificate.
     * @return the created endpoint.
     */
    protected HttpEndpointBuilderFactory.HttpEndpointBuilder setUpITEndpoint() {
        final KeyStoreParameters keyStoreParameters = new KeyStoreParameters();
        keyStoreParameters.setResource(this.emailConnectorConfig.getItKeyStoreLocation());
        keyStoreParameters.setPassword(this.emailConnectorConfig.getItKeyStorePassword());

        final KeyManagersParameters keyManagersParameters = new KeyManagersParameters();
        keyManagersParameters.setKeyPassword(this.emailConnectorConfig.getItKeyStorePassword());
        keyManagersParameters.setKeyStore(keyStoreParameters);

        final SSLContextParameters sslContextParameters = new SSLContextParameters();
        sslContextParameters.setKeyManagers(keyManagersParameters);

        // Remove the schema from the url to avoid the
        // "ResolveEndpointFailedException", which complaints about specifying
        // the schema twice.
        final String fullURL = this.emailConnectorConfig.getItUserServiceURL();
        if (fullURL.startsWith("https")) {
            return https(fullURL.replace("https://", ""))
                .sslContextParameters(sslContextParameters);
        } else {
            return http(fullURL.replace("http://", ""));
        }
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
            final TrustManagersParameters trustManagersParameters = new TrustManagersParameters();
            trustManagersParameters.setTrustManager(new BOPTrustManager());

            final SSLContextParameters sslContextParameters = new SSLContextParameters();
            sslContextParameters.setTrustManagers(trustManagersParameters);

            return https(fullURL.replace("https://", ""))
                .sslContextParameters(sslContextParameters)
                .x509HostnameVerifier(NoopHostnameVerifier.INSTANCE);
        } else {
            return http(fullURL.replace("http://", ""));
        }
    }
}
