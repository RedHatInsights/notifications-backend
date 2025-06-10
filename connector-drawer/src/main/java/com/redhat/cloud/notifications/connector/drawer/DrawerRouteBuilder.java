package com.redhat.cloud.notifications.connector.drawer;

import com.redhat.cloud.notifications.connector.EngineToConnectorRouteBuilder;
import com.redhat.cloud.notifications.connector.drawer.config.DrawerConnectorConfig;
import com.redhat.cloud.notifications.connector.drawer.recipients.RecipientsResolverPreparer;
import com.redhat.cloud.notifications.connector.drawer.recipients.RecipientsResolverResponseProcessor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Predicate;
import java.util.Set;

import static com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder.SUCCESS;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.*;
import static com.redhat.cloud.notifications.connector.drawer.constant.ExchangeProperty.RESOLVED_RECIPIENT_LIST;
import static com.redhat.cloud.notifications.connector.drawer.constant.ExchangeProperty.USE_SIMPLIFIED_ROUTE;
import static org.apache.camel.LoggingLevel.DEBUG;

@ApplicationScoped
public class DrawerRouteBuilder extends EngineToConnectorRouteBuilder {

    static final String RECIPIENTS_RESOLVER_RESPONSE_TIME_METRIC = "micrometer:timer:drawer.recipients_resolver.response.time";
    static final String TIMER_ACTION_START = "?action=start";
    static final String TIMER_ACTION_STOP = "?action=stop";

    @Inject
    DrawerConnectorConfig drawerConnectorConfig;

    @Inject
    RecipientsResolverPreparer recipientsResolverPreparer;

    @Inject
    RecipientsResolverResponseProcessor recipientsResolverResponseProcessor;

    @Inject
    DrawerPayloadBuilder drawerPayloadBuilder;

    @Inject
    DrawerProcessor drawerProcessor;

    public static final String CONNECTOR_TO_DRAWER = "connector-to-drawer";

    @Override
    public void configureRoutes() {
        from(seda(ENGINE_TO_CONNECTOR))
            .routeId(drawerConnectorConfig.getConnectorName())
            .process(recipientsResolverPreparer)
            .choice()
                .when(shouldUseSimplifiedManagement())
                    .process(drawerProcessor)
                .otherwise()
                    .to(RECIPIENTS_RESOLVER_RESPONSE_TIME_METRIC + TIMER_ACTION_START)
                        .to(drawerConnectorConfig.getRecipientsResolverServiceURL() + "/internal/recipients-resolver")
                    .to(RECIPIENTS_RESOLVER_RESPONSE_TIME_METRIC + TIMER_ACTION_STOP)
                    .process(recipientsResolverResponseProcessor)
                    .choice().when(hasRecipients())
                        .process(drawerPayloadBuilder)
                        .to(log(getClass().getName()).level("INFO").showHeaders(true).showBody(true))
                        .to(direct(CONNECTOR_TO_DRAWER))
                        .log(DEBUG, getClass().getName(), "Sent Drawer notification " +
                            "[orgId=${exchangeProperty." + ORG_ID + "}, historyId=${exchangeProperty." + ID + "}]")
                    .end()
            .end()
            .to(direct(SUCCESS));

        from(direct(CONNECTOR_TO_DRAWER))
            .routeId(CONNECTOR_TO_DRAWER)
            .to(kafka(drawerConnectorConfig.getOutgoingDrawerTopic()));
    }

    private Predicate shouldUseSimplifiedManagement() {
        return exchange -> exchange.getProperty(USE_SIMPLIFIED_ROUTE, false, Boolean.class);
    }

    private Predicate hasRecipients() {
        return exchange -> !exchange.getProperty(RESOLVED_RECIPIENT_LIST, Set.class).isEmpty();
    }

}
