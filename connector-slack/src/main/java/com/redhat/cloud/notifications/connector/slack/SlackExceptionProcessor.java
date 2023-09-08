package com.redhat.cloud.notifications.connector.slack;

import com.redhat.cloud.notifications.connector.ExceptionProcessor;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.redhat.cloud.notifications.connector.slack.ExchangeProperty.CHANNEL;
import static org.jboss.logging.Logger.Level;
import static org.jboss.logging.Logger.Level.DEBUG;
import static org.jboss.logging.Logger.Level.ERROR;

@ApplicationScoped
public class SlackExceptionProcessor extends ExceptionProcessor {

    private static final Pattern CHANNEL_NOT_FOUND_PATTERN = Pattern.compile(".+code=404.+body=channel_not_found.+");
    private static final String LOG_MSG = "Message sending failed on %s: [orgId=%s, historyId=%s, webhookUrl=%s, channel=%s]";

    @Override
    protected void process(Throwable t, Exchange exchange) {
        if (t instanceof CamelExchangeException e) {
            Matcher matcher = CHANNEL_NOT_FOUND_PATTERN.matcher(e.getMessage());
            if (matcher.matches()) {
                // TODO Disable the integration using the 'integration-disabled' event type.
                log(DEBUG, t, exchange);
            } else {
                log(ERROR, t, exchange);
            }
        } else {
            log(ERROR, t, exchange);
        }
    }

    private void log(Level level, Throwable t, Exchange exchange) {
        Log.logf(
                level,
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
