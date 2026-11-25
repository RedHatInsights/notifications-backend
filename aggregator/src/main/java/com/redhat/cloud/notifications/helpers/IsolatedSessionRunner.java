package com.redhat.cloud.notifications.helpers;

import io.quarkus.arc.Arc;
import io.quarkus.arc.InjectableContext;
import io.quarkus.arc.ManagedContext;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.function.Supplier;

/**
 * Runs a piece of work in its OWN request context, and therefore with its own Hibernate session, and gives that session
 * (with every entity it loaded) up when the work ends.
 * <p>
 * The aggregator runs the whole job inside one request context, so without this every entity loaded by any org would stay
 * referenced by one session until the run ends. The engine does not have that problem because its request context is one
 * Kafka message (one trigger). Everything that crosses the boundary must be plain data: entities of the inner session are
 * detached when it ends.
 */
@ApplicationScoped
public class IsolatedSessionRunner {

    public <T> T call(Supplier<T> work) {
        ManagedContext requestContext = Arc.container().requestContext();
        // The outer context (if any) is only set aside, not destroyed: its entities stay valid for the caller.
        InjectableContext.ContextState outer = requestContext.isActive() ? requestContext.getState() : null;
        if (outer != null) {
            requestContext.deactivate();
        }
        requestContext.activate();
        try {
            return work.get();
        } finally {
            requestContext.terminate();
            if (outer != null) {
                requestContext.activate(outer);
            }
        }
    }
}
