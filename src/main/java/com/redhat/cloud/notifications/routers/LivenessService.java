package com.redhat.cloud.notifications.routers;

import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
@Readiness
public class LivenessService implements HealthCheck {

//    @Inject
//    HealthCenter messagingHealth;

    @Inject
    Uni<PostgresqlConnection> connectionPublisherUni;

//    public Uni<Boolean> getLiveness() {
//        Uni<Boolean> postgresHealth = postgresConnectionHealth();
//        Uni<Boolean> kafkaHealth = Uni.createFrom().item(messagingHealth.getReadiness())
//                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
//                .map(hr -> {
//                    for (HealthReport.ChannelInfo channel : hr.getChannels()) {
//                        if (!channel.isOk()) {
//                            return false;
//                        }
//                    }
//                    return true;
//                });
//
//        return Uni.combine().all().unis(postgresHealth, kafkaHealth).combinedWith((p, k) -> p && k);
//    }

    Uni<Boolean> postgresConnectionHealth() {
        return connectionPublisherUni.toMulti()
                .onItem().transform(conn -> conn.createStatement("SELECT COUNT(1)").execute())
                .flatMap(flux -> flux
                        .flatMap(postgresqlResult -> postgresqlResult.map((row, rowMetadata) -> true)))
                .toUni()
                .onFailure().recoverWithItem(false);
    }

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder response = HealthCheckResponse.named("Notifications Engine readiness check")
                .state(postgresConnectionHealth().await().indefinitely());

        return response.build();
    }
}
