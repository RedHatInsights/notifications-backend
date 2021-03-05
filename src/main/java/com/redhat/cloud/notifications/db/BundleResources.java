package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.db.entities.ApplicationEntity;
import com.redhat.cloud.notifications.db.entities.BundleEntity;
import com.redhat.cloud.notifications.db.mappers.ApplicationMapper;
import com.redhat.cloud.notifications.db.mappers.BundleMapper;
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

    @Inject
    BundleMapper bundleMapper;

    @Inject
    ApplicationMapper applicationMapper;

    public Uni<Bundle> createBundle(Bundle bundle) {
        // Return filled with id
        return Uni.createFrom().item(() -> bundleMapper.dtoToEntity(bundle))
                .flatMap(bundleEntity -> session.persist(bundleEntity)
                        .call(() -> session.flush())
                        .replaceWith(bundleEntity)
                )
                .onItem().transform(bundleMapper::entityToDto);
    }

    public Multi<Bundle> getBundles() {
        String query = "FROM BundleEntity";
        return session.createQuery(query, BundleEntity.class)
                .getResultList()
                .onItem().transformToMulti(Multi.createFrom()::iterable)
                .onItem().transform(bundleMapper::entityToDto);
    }

    public Uni<Bundle> getBundle(UUID bundleId) {
        return session.find(BundleEntity.class, bundleId)
                .onItem().ifNotNull().transform(bundleMapper::entityToDto);
    }

    public Uni<Boolean> deleteBundle(UUID bundleId) {
        String query = "DELETE FROM BundleEntity WHERE id = :bundleId";
        return session.createQuery(query)
                .setParameter("bundleId", bundleId)
                .executeUpdate()
                .call(() -> session.flush())
                .onItem().transform(rowCount -> rowCount > 0);
    }

    public Multi<Application> getApplications(UUID bundleId) {
        String query = "FROM ApplicationEntity WHERE bundle.id = :bundleId";
        return session.createQuery(query, ApplicationEntity.class)
                .setParameter("bundleId", bundleId)
                .getResultList()
                .onItem().transformToMulti(Multi.createFrom()::iterable)
                .onItem().transform(applicationMapper::entityToDtoWithoutTimestamps);
    }

    public Uni<Application> addApplicationToBundle(UUID bundleId, Application app) {
        return Uni.createFrom().item(() -> {
            app.setBundleId(bundleId);
            return applicationMapper.dtoToEntity(app);
        })
        .flatMap(applicationEntity -> session.persist(applicationEntity)
                .replaceWith(applicationEntity))
        .call(() -> session.flush())
        .onItem().transform(applicationMapper::entityToDto);
    }
}
