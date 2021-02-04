package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import io.r2dbc.postgresql.api.PostgresqlResult;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import reactor.core.publisher.Flux;

import javax.enterprise.context.ApplicationScoped;
import java.util.Date;
import java.util.UUID;

/**
 * Deal with Bundles.
 * More or less stupid copy & paste & search & replace of
 * code in BundleResources
 */
@ApplicationScoped
public class BundleResources extends  AbstractGenericResource {

    public static final String PUBLIC_APPLICATIONS = "public.applications";
    public static final String PUBLIC_BUNDLES = "public.bundles";

    public Uni<Bundle> createBundle(Bundle bundle) {
        String query = "INSERT INTO " + PUBLIC_BUNDLES + " (name, display_name) VALUES ($1, $2)";
        // Return filled with id
        return connectionPublisher.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                    c2 -> c2.createStatement(query)
                    .bind("$1", bundle.getName())
                    .bind("$2", bundle.getDisplay_name())
                    .returnGeneratedValues("id", "created")
                    .execute()
                    .flatMap(res -> res.map((row, rowMetadata) -> {
                        bundle.setId(row.get("id", UUID.class));
                        bundle.setCreated(row.get("created", Date.class));
                        return bundle;
                    })))
            .withFinalizer(postgresqlConnection -> {
                postgresqlConnection.close().subscribe();
            }))
                .toUni();
    }

    private static final String BUNDLE_QUERY = "SELECT b.id, b.name, b.display_name, b.created, b.updated FROM " + PUBLIC_BUNDLES + " b";

    public Multi<Bundle> getBundles() {
        return connectionPublisher.get().onItem()
            .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                c2 -> {
                    Flux<PostgresqlResult> execute = c2.createStatement(BUNDLE_QUERY)
                        .execute();

                    return mapResultSetToBundle(execute);
                })
                .withFinalizer(postgresqlConnection -> {
                    postgresqlConnection.close().subscribe();
                }));
    }

    private Flux<Bundle> mapResultSetToBundle(Flux<PostgresqlResult> resultFlux) {
        return resultFlux.flatMap(postgresqlResult -> postgresqlResult.map((row, rowMetadata) -> {
            Bundle bundle = new Bundle();
            bundle.setId(row.get("id", UUID.class));
            bundle.setName(row.get("name", String.class));
            bundle.setDisplay_name(row.get("display_name", String.class));
            bundle.setCreated(row.get("created", Date.class));
            bundle.setUpdated(row.get("updated", Date.class));
            return bundle;
        }));
    }

    public Uni<Bundle> getBundle(UUID bundleId) {
        String query = BUNDLE_QUERY + " WHERE id = $1";
        return connectionPublisher.get().onItem()
            .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                c2 -> {
                    Flux<PostgresqlResult> execute = c2.createStatement(query)
                            .bind("$1", bundleId)
                            .execute();

                    return mapResultSetToBundle(execute);
                })
                .withFinalizer(postgresqlConnection -> {
                    postgresqlConnection.close().subscribe();
                })).toUni();
    }

    public Uni<Boolean> deleteBundle(UUID bundleId) {
        String query = "DELETE FROM " + PUBLIC_BUNDLES + " WHERE id = $1";

        return runDeleteQuery(bundleId, query);
    }


    public Multi<Application> getApplications(UUID bundleId) {
        String query = "SELECT et.id, et.name, et.display_name FROM " + PUBLIC_APPLICATIONS + " et " +
                "WHERE et.bundle_id = $1";

        return connectionPublisher.get().onItem()
            .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                c2 -> {
                    Flux<PostgresqlResult> execute = c2.createStatement(query)
                            .bind("$1", bundleId)
                            .execute();

                    return execute.flatMap(res -> res.map((row, rowMetadata) -> {
                        Application application = new Application();
                        application.setId(row.get("id", UUID.class));
                        application.setName(row.get("name", String.class));
                        application.setDisplay_name(row.get("display_name", String.class));
                        application.setBundleId(bundleId);
                        return application;
                    }));
                })
                .withFinalizer(postgresqlConnection -> {
                    postgresqlConnection.close().subscribe();
                }));
    }

    public Uni<Application> addApplicationToBundle(UUID bundleId, Application app) {
        String insertQuery = "INSERT INTO " + PUBLIC_APPLICATIONS + " (name, display_name, bundle_Id) VALUES ($1, $2, $3)";

        return connectionPublisher.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                    c2 ->  c2.createStatement(insertQuery)
                            .bind("$1", app.getName())
                            .bind("$2", app.getDisplay_name())
                            .bind("$3", bundleId)
                            .returnGeneratedValues("id")
                            .execute()
                            .flatMap(res -> res.map((row, rowMetadata) -> {
                                app.setId(row.get("id", UUID.class));
                                return app;
                            }))

                )
                        .withFinalizer(postgresqlConnection -> {
                            postgresqlConnection.close().subscribe();
                        }))
                .toUni();
    }


}
