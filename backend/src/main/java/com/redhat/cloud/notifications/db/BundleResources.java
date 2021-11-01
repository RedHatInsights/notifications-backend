package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.NotFoundException;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class BundleResources {

    @Inject
    Mutiny.SessionFactory sessionFactory;

    public Uni<Bundle> createBundle(Bundle bundle) {
        // The returned bundle will contain an ID and a creation timestamp.
        return sessionFactory.withSession(session -> {
            return Uni.createFrom().item(bundle).onItem().transformToUni(session::persist).call(session::flush)
                    .replaceWith(bundle);
        });
    }

    public Uni<List<Bundle>> getBundles() {
        String query = "FROM Bundle";
        return sessionFactory.withSession(session -> {
            return session.createQuery(query, Bundle.class).getResultList();
        });
    }

    public Uni<Bundle> getBundle(UUID id) {
        return sessionFactory.withSession(session -> {
            return session.find(Bundle.class, id);
        });
    }

    public Uni<Bundle> getBundle(String name) {
        String query = "FROM Bundle WHERE name = :name";
        return sessionFactory.withSession(session -> {
            return session.createQuery(query, Bundle.class).setParameter("name", name).getSingleResultOrNull();
        });
    }

    public Uni<Integer> updateBundle(UUID id, Bundle bundle) {
        String query = "UPDATE Bundle SET name = :name, displayName = :displayName WHERE id = :id";
        return sessionFactory.withSession(session -> {
            return session.createQuery(query).setParameter("name", bundle.getName())
                    .setParameter("displayName", bundle.getDisplayName()).setParameter("id", id).executeUpdate()
                    .call(session::flush);
        });
    }

    public Uni<Boolean> deleteBundle(UUID id) {
        String query = "DELETE FROM Bundle WHERE id = :id";
        return sessionFactory.withSession(session -> {
            return session.createQuery(query).setParameter("id", id).executeUpdate().call(session::flush).onItem()
                    .transform(rowCount -> rowCount > 0);
        });
    }

    public Uni<List<Application>> getApplications(UUID id) {
        String query = "FROM Application WHERE bundle.id = :id";
        return sessionFactory.withSession(session -> {
            return session.find(Bundle.class, id).onItem().ifNull().failWith(new NotFoundException())
                    .replaceWith(session.createQuery(query, Application.class).setParameter("id", id).getResultList());
        });
    }
}
