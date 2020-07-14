package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.WebhookAttributes;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.UniEmitter;
import org.hibernate.reactive.mutiny.Mutiny;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.function.BiConsumer;
import java.util.function.Function;

@ApplicationScoped
public class EndpointResources {

    @Inject
    Uni<Mutiny.Session> mutinySession;

    public Uni<Endpoint> getEndpoint(String tenant, String id) {
        return null;
    }

    public Multi<Endpoint> getEndpoints(String tenant) {
        return mutinySession.onItem().produceMulti(session -> session.createNativeQuery(String.format("SELECT * FROM public.endpoints WHERE tenant = %s", tenant), Endpoint.class).getResults())
                // TODO In next phase, we change the table based on the endpoint type in the endpoint
                .onItem().produceUni(endpoint -> mutinySession.onItem().produceUni(session -> session.createNativeQuery(String.format("SELECT * FROM public.endpoint_webhooks WHERE endpoint_id = %s", endpoint.getId()), WebhookAttributes.class).getSingleResult())
                        .onItem().apply(wa -> {
                            endpoint.setProperties(wa);
                            return endpoint;
                        })).merge();
    }

    public Uni<Endpoint> createEndpoint(Endpoint endpoint) {
        return null;
    }

    public Uni<Void> deleteEndpoint(String tenant, String id) {
        return null;
    }

    public Uni<Endpoint> updateEndpoint(Endpoint endpoint) {
        return null;
    }

    // createHistoryEvent
    // getHistoryEvents
    // getHistoryEventDetails
}
