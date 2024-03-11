package com.redhat.cloud.notifications.connector;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import java.util.Optional;

/**
 * This predicate determine if connector request to continue normal flow on exception or not.
 * It useful to aggregate results for a loop process to be able to send only one response to engine.
 */
@DefaultBean
@ApplicationScoped
public class ContinueOnErrorPredicate implements Predicate {

    public static String MUST_CONTINUE_ON_EXCEPTION = "must_continue_on_exception";

    @Override
    public boolean matches(Exchange exchange) {
        Optional<Boolean> mustContinueOnException = Optional.ofNullable(exchange.getProperty(MUST_CONTINUE_ON_EXCEPTION, Boolean.class));
        return mustContinueOnException.isPresent() && mustContinueOnException.get();
    }

}
