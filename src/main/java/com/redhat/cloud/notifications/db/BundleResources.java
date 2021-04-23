package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.UUID;

/**
 * Deal with Bundles.
 */
@ApplicationScoped
public class BundleResources {

    @Inject
    Mutiny.Session session;

    public Uni<Bundle> createBundle(Bundle bundle) {
        // The returned bundle will contain an ID and a creation timestamp.
        return Uni.createFrom().item(bundle)
                .onItem().transformToUni(session::persist)
                .call(session::flush)
                .replaceWith(bundle);
    }

    public Multi<Bundle> getBundles() {
        String query = "FROM Bundle";
        return session.createQuery(query, Bundle.class)
                .getResultList()
                .onItem().transformToMulti(Multi.createFrom()::iterable);
    }

    public Uni<Bundle> getBundle(UUID id) {
        return session.find(Bundle.class, id);
    }

    public Uni<Bundle> getBundle(String name) {
        String query = "FROM Bundle WHERE name = :name";
        return session.createQuery(query, Bundle.class)
                .setParameter("name", name)
                .getSingleResultOrNull();
    }

    public Uni<Integer> updateBundle(UUID id, Bundle bundle) {
        String query = "UPDATE Bundle SET name = :name, displayName = :displayName WHERE id = :id";
        return session.createQuery(query)
                .setParameter("name", bundle.getName())
                .setParameter("displayName", bundle.getDisplayName())
                .setParameter("id", id)
                .executeUpdate()
                .call(session::flush);
    }

    public Uni<Boolean> deleteBundle(UUID id) {
        String query = "DELETE FROM Bundle WHERE id = :id";
        return session.createQuery(query)
                .setParameter("id", id)
                .executeUpdate()
                .call(session::flush)
                .onItem().transform(rowCount -> rowCount > 0);
    }

    public Multi<Application> getApplications(UUID id) {
        String query = "FROM Application WHERE bundle.id = :id";
        return session.createQuery(query, Application.class)
                .setParameter("id", id)
                .getResultList()
                .onItem().transformToMulti(Multi.createFrom()::iterable);
    }

    public Uni<Application> addApplicationToBundle(UUID bundleId, Application app) {
        return Uni.createFrom().item(app)
                .onItem().transform(a -> addBundleReference(a, bundleId))
                .onItem().transformToUni(session::persist)
                .call(session::flush)
                .replaceWith(app);
    }

    /**
     * Adds to the given {@link Application} a reference to a persistent {@link Bundle} without actually loading its
     * state from the database. The app will remain unchanged if the given bundle identifier is null.
     *
     * @param app the app that will hold the bundle reference
     * @param bundleId the persistent bundle identifier
     * @return the same app instance, possibly modified if a bundle reference was added
     */
    private Application addBundleReference(Application app, UUID bundleId) {
        if (bundleId != null && app.getBundle() == null) {
            app.setBundle(session.getReference(Bundle.class, bundleId));
        }
        return app;
    }
}
