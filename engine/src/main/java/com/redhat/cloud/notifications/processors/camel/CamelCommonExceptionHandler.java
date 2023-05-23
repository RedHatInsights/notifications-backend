package com.redhat.cloud.notifications.processors.camel;

import com.redhat.cloud.notifications.processors.camel.slack.SlackRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import javax.inject.Inject;
import java.io.IOException;

import static com.redhat.cloud.notifications.processors.camel.ExchangeProperty.OUTCOME;
import static com.redhat.cloud.notifications.processors.camel.ExchangeProperty.SUCCESSFUL;
import static org.apache.camel.LoggingLevel.ERROR;
import static org.apache.camel.LoggingLevel.INFO;
import static org.apache.camel.builder.endpoint.StaticEndpointBuilders.direct;

public abstract class CamelCommonExceptionHandler extends RouteBuilder {

    @ConfigProperty(name = "notifications.camel.max-endpoint-cache-size", defaultValue = "100")
    protected int maxEndpointCacheSize;

    @ConfigProperty(name = "notifications.camel.maximum-redeliveries", defaultValue = "2")
    int maxRedeliveries;

    @ConfigProperty(name = "notifications.camel.redelivery-delay", defaultValue = "1000")
    long redeliveryDelay;

    @Inject
    RetryCounterProcessor retryCounterProcessor;

    protected static final String CAMEL_HTTP_HEADERS_PATTERN = "CamelHttp*";

    private static final String COMMON_ERROR_MSG = "Message sending failed on ${routeId}: [orgId=${exchangeProperty.orgId}, " +
        "historyId=${exchangeProperty.historyId}, webhookUrl=${exchangeProperty.webhookUrl}] ";

    private static final String ERROR_MSG_WITH_CHANNEL_PREFIX = "Message sending failed on ${routeId}: [orgId=${exchangeProperty.orgId}, " +
        "historyId=${exchangeProperty.historyId}, webhookUrl=${exchangeProperty.webhookUrl}, channel=${exchangeProperty.channel}] ";

    private static final String ERROR_MSG_HTTP_OPERATION_FAILED = COMMON_ERROR_MSG + "with status code: [${exception.statusCode}] and body [${exception.responseBody}]";

    private static final String ERROR_MSG = COMMON_ERROR_MSG + "\n${exception.stacktrace}";

    private static final String ERROR_MSG_WITH_CHANNEL_HTTP_OPERATION_FAILED = ERROR_MSG_WITH_CHANNEL_PREFIX + "with status code: [${exception.statusCode}] and body [${exception.responseBody}]";

    private static final String ERROR_MSG_WITH_CHANNEL = ERROR_MSG_WITH_CHANNEL_PREFIX + "\n${exception.stacktrace}";

    private static final String IS_INTEGRATION_WITH_CHANNEL_EXPRESSION =    "${routeId} in '" + SlackRouteBuilder.SLACK_INCOMING_ROUTE + "," + SlackRouteBuilder.SLACK_OUTGOING_ROUTE + "'";

    protected void configureCommonExceptionHandler() {

        /*
         * An IOException can be thrown in case of network issue or of unexpected remote server failure.
         * It is worth retrying in that case. Retry attempts will be logged. The exception itself will also be
         * logged eventually if none of the retry attempts were successful.
         */
        onException(IOException.class)
            .handled(true)
            .maximumRedeliveries(maxRedeliveries)
            .redeliveryDelay(redeliveryDelay)
            .onRedelivery(retryCounterProcessor)
            .retryAttemptedLogLevel(INFO)
            .choice()
                .when(simple(IS_INTEGRATION_WITH_CHANNEL_EXPRESSION))
                    .log(ERROR, ERROR_MSG_WITH_CHANNEL)
                .otherwise()
                    .log(ERROR, ERROR_MSG)
            .end()
            .setProperty(SUCCESSFUL, constant(false))
            .setProperty(OUTCOME, simple("${exception.message}"))
            .to(direct("return"));

        /*
         * Simply logs more details than what Camel provides by default in case of HTTP error.
         */
        onException(HttpOperationFailedException.class)
            .handled(true)
            .choice()
                .when(simple(IS_INTEGRATION_WITH_CHANNEL_EXPRESSION))
                    .log(ERROR, ERROR_MSG_WITH_CHANNEL_HTTP_OPERATION_FAILED)
                .otherwise()
                    .log(ERROR, ERROR_MSG_HTTP_OPERATION_FAILED)
            .end()
            .setProperty(SUCCESSFUL, constant(false))
            .setProperty(OUTCOME, simple("${exception.message}"))
            .to(direct("return"));

        /*
         * Simply logs more details than what Camel provides by default.
         */
        onException(Exception.class)
            .handled(true)
            .choice()
                .when(simple(IS_INTEGRATION_WITH_CHANNEL_EXPRESSION))
                    .log(ERROR, ERROR_MSG_WITH_CHANNEL)
                .otherwise()
                    .log(ERROR, ERROR_MSG)
            .end()
            .setProperty(SUCCESSFUL, constant(false))
            .setProperty(OUTCOME, simple("${exception.message}"))
            .to(direct("return"));
    }
}
