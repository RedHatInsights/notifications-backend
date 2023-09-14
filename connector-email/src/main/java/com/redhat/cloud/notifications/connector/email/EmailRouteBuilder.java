package com.redhat.cloud.notifications.connector.email;

import com.redhat.cloud.notifications.connector.ConnectorConfig;
import com.redhat.cloud.notifications.connector.EngineToConnectorRouteBuilder;
import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import com.redhat.cloud.notifications.connector.email.constants.Routes;
import com.redhat.cloud.notifications.connector.email.predicates.NotFinishedFetchingAllPages;
import com.redhat.cloud.notifications.connector.email.predicates.rbac.StatusCodeNotFound;
import com.redhat.cloud.notifications.connector.email.processors.bop.BOPRequestPreparer;
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
import org.apache.camel.Endpoint;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.component.http.HttpComponent;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;

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
     * Configures the flow for this connector.
     * @throws Exception if the IT SSL route could not be correctly set up.
     */
    @Override
    public void configureRoute() throws Exception {
        from(direct(ENGINE_TO_CONNECTOR))
            .routeId(this.connectorConfig.getConnectorName())
            /*
             * Dispatches the exchanges either to fetch the users directly, or, if
             * a group ID is present, fetch the user from that group from RBAC.
             */
            .process(this.dispatcherProcessor);

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
            .process(this.recipientsFilter)
            .to(direct(Routes.SEND_EMAIL_BOP_CHOICE));

        /*
         * Fetches the users from IT and filters them. The IT's endpoint is set
         * up separately because we need to create a special SSL context which
         * trusts IT's certificate.
         */
        final Endpoint itEndpoint = this.setUpITEndpoint();

        from(direct(Routes.FETCH_USERS_IT))
            .routeId(Routes.FETCH_USERS_IT)
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
            .process(this.recipientsFilter)
            .to(direct(Routes.SEND_EMAIL_BOP_CHOICE));

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
                    .process(this.recipientsFilter)
                    .to(direct(Routes.SEND_EMAIL_BOP_CHOICE));

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
            .process(this.recipientsFilter)
            .to(direct(Routes.SEND_EMAIL_BOP_CHOICE));

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
        from(direct(Routes.SEND_EMAIL_BOP))
            .routeId(Routes.SEND_EMAIL_BOP)
            // Clear all the headers that may come from the previous route.
            .removeHeaders("*")
            .process(this.BOPRequestPreparer)
            .to(this.emailConnectorConfig.getBopURL())
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
            .split(simpleF("${exchangeProperty.%s}", ExchangeProperty.USERNAMES))
                .setProperty(ExchangeProperty.SINGLE_EMAIL_PER_USER, constant(true))
                .to(direct(Routes.SEND_EMAIL_BOP))
            .end();
    }

    /**
     * Creates the endpoint for the IT Users service. It makes Apache Camel
     * trust the service's certificate.
     * @return the created endpoint.
     * @throws Exception if the endpoint could not be created due to an
     * unexpected error.
     */
    private Endpoint setUpITEndpoint() throws Exception {
        final KeyStoreParameters ksp = new KeyStoreParameters();
        ksp.setResource(this.emailConnectorConfig.getItKeyStoreLocation());
        ksp.setPassword(this.emailConnectorConfig.getItKeyStorePassword());

        final TrustManagersParameters tmp = new TrustManagersParameters();
        tmp.setKeyStore(ksp);

        final SSLContextParameters scp = new SSLContextParameters();
        scp.setTrustManagers(tmp);

        final HttpComponent httpComponent = this.getCamelContext().getComponent("https", HttpComponent.class);
        httpComponent.setSslContextParameters(scp);

        return httpComponent.createEndpoint(String.format("https:%s", this.emailConnectorConfig.getItUserServiceURL()));
    }
}
