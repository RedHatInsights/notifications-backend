package com.redhat.cloud.notifications.connector.slack;

import com.redhat.cloud.notifications.connector.ExceptionProcessor;
import io.quarkus.logging.Log;
import org.apache.camel.Exchange;

import javax.enterprise.context.ApplicationScoped;

import static com.redhat.cloud.notifications.connector.slack.ExchangeProperty.CHANNEL;

@ApplicationScoped
public class SlackExceptionProcessor extends ExceptionProcessor {

    private static final String LOG_MSG = "Message sending failed on %s: [orgId=%s, historyId=%s, webhookUrl=%s, channel=%s]";

    @Override
    protected void log(Throwable t, Exchange exchange) {
        Log.errorf(
                t,
                LOG_MSG,
                getRouteId(exchange),
                getOrgId(exchange),
                getExchangeId(exchange),
                getTargetUrl(exchange),
                exchange.getProperty(CHANNEL, String.class)
        );
    }
}
