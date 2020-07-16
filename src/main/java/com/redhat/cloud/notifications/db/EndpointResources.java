package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.WebhookAttributes;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

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
        // TODO This doesn't merge properties based on the type (such as webhooks) - nor do errors if that wasn't possible
        return mutinySession.onItem().produceUni(session -> session.persist(endpoint))
                .onItem().produceUni(Mutiny.Session::flush)
                .onItem().apply(ignored -> endpoint)
                .onFailure().invoke(t -> {
                    System.out.printf("Failed to persist, %s\n", t.getMessage());
                });
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
