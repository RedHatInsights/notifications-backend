package com.redhat.cloud.notifications.routers.internal.kessel;

import com.redhat.cloud.notifications.auth.ConsoleIdentityProvider;
import com.redhat.cloud.notifications.auth.kessel.ResourceType;
import com.redhat.cloud.notifications.auth.rbac.workspace.WorkspaceUtils;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.models.Endpoint;
import io.grpc.stub.StreamObserver;
import io.quarkus.logging.Log;
import io.quarkus.security.UnauthorizedException;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.project_kessel.api.relations.v1beta1.CreateTuplesRequest;
import org.project_kessel.api.relations.v1beta1.CreateTuplesResponse;
import org.project_kessel.api.relations.v1beta1.ImportBulkTuplesRequest;
import org.project_kessel.api.relations.v1beta1.ImportBulkTuplesResponse;
import org.project_kessel.api.relations.v1beta1.ObjectReference;
import org.project_kessel.api.relations.v1beta1.ObjectType;
import org.project_kessel.api.relations.v1beta1.Relationship;
import org.project_kessel.api.relations.v1beta1.SubjectReference;
import org.project_kessel.relations.client.RelationTuplesClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;

@ApplicationScoped
@RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
@Path(API_INTERNAL)
public class KesselAssetsMigrationService {
    /**
     * Defines the relation between the subject and the resource when passing
     * the tuple to Kessel. <a href="https://github.com/RedHatInsights/rbac-config/blob/a806fb03c95959391eceb0b42c7eefd8ae2350ae/configs/prod/schemas/schema.zed#L3">
     * Reference</a>
     */
    public static final String RELATION = "workspace";

    @Inject
    BackendConfig backendConfig;

    @Inject
    EndpointRepository endpointRepository;

    @Inject
    RelationTuplesClient relationTuplesClient;

    @Inject
    WorkspaceUtils workspaceUtils;

    @Path("/kessel/migrate-assets")
    @POST
    public void migrateAssets() {
        int fetchedEndpointsSize = 0;
        int offset = 0;
        do {
            final List<Endpoint> fetchedEndpoints = this.endpointRepository.getNonSystemEndpointsWithLimitAndOffset(this.backendConfig.getKesselMigrationBatchSize(), offset);

            // If for some reason we have fetched full pages from the database
            // all the time, the last one might be empty, so there is no need
            // to attempt calling Kessel.
            if (fetchedEndpoints.isEmpty()) {
                break;
            }

            final CreateTuplesRequest request = this.createTuplesRequest(fetchedEndpoints);
            final int finalOffset = offset;
            this.relationTuplesClient.createTuples(request, new StreamObserver<>() {
                @Override
                public void onNext(final CreateTuplesResponse createTuplesResponse) {
                }

                @Override
                public void onError(final Throwable throwable) {
                    Log.errorf(throwable, "[offset: %s][first_integration: %s][last_integration: %s] Unable to send batch of tuples to Kessel with offset %s", finalOffset, fetchedEndpoints.getFirst().getId(), fetchedEndpoints.getLast().getId());
                }

                @Override
                public void onCompleted() {
                    Log.infof("[offset: %s][first_integration: %s][last_integration: %s] Sent % integrations to Kessel", finalOffset, fetchedEndpoints.getFirst().getId(), fetchedEndpoints.getLast().getId(), fetchedEndpoints.size());
                }
            });

            fetchedEndpointsSize = fetchedEndpoints.size();
            offset += fetchedEndpoints.size();
        } while (fetchedEndpointsSize == this.backendConfig.getKesselMigrationBatchSize());

        Log.infof("Finished migrating integrations to the Kessel inventory");
    }

    @Path("/kessel/migrate-assets/async")
    @POST
    public void migrateAssetsAsync() {
        final int limit = this.backendConfig.getKesselMigrationBatchSize();

        final AtomicInteger offsetContainer = new AtomicInteger(0);
        final Multi<List<Endpoint>> integrationBatch = Multi
            .createBy()
            .repeating()
            .supplier(
                () -> offsetContainer.addAndGet(limit),
                offset -> {
                    final List<Endpoint> endpoints = this.endpointRepository.getNonSystemEndpointsWithLimitAndOffset(limit, offset);
                    Log.infof("[limit: %s][offset: %s] Fetched endpoint batch from the database", limit, offset);

                    return endpoints;
                }
            ).whilst(endpoints -> endpoints.size() == limit);

        final Multi<ImportBulkTuplesRequest> importBulkTuplesRequestMulti = integrationBatch
            .map(endpoints -> endpoints.stream().map(this::mapEndpointToRelationship).toList())
            .map(relationships -> ImportBulkTuplesRequest.newBuilder().addAllTuples(relationships).build());

        final Uni<ImportBulkTuplesResponse> importBulkTuplesResponseUni = this.relationTuplesClient.importBulkTuplesUni(importBulkTuplesRequestMulti);

        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final ImportBulkTuplesResponse response = importBulkTuplesResponseUni
            .onFailure().invoke(failure::set)
            .onFailure().recoverWithNull()
            .await().indefinitely();

        if (response == null) {
            Log.errorf(failure.get(), "Unable to import integrations into Kessel");
        } else {
            Log.infof("%s endpoints imported into Kessel", response.getNumImported());
        }
    }

    /**
     * Creates a bulk import request ready to be sent to kessel.
     * @param endpoints the list of endpoints to create the bulk import request
     *                  from.
     * @return the bulk import request ready to be sent.
     */
    protected CreateTuplesRequest createTuplesRequest(final List<Endpoint> endpoints) {
        final List<Relationship> relations = new ArrayList<>(endpoints.size());

        for (final Endpoint endpoint : endpoints) {
            try {
                relations.add(this.mapEndpointToRelationship(endpoint));
            } catch (final UnauthorizedException e) {
                Log.errorf("[org_id: %s][endpoint_id: %s] Unable to get the default workspace for integration", endpoint.getOrgId(), endpoint.getId());
            }
        }

        return CreateTuplesRequest
            .newBuilder()
            .addAllTuples(relations)
            .build();
    }

    /**
     * Maps an endpoint to a Kessel relationship.
     * @param endpoint the endpoint to map.
     * @return the generated relationship.
     */
    protected Relationship mapEndpointToRelationship(final Endpoint endpoint) {
        return Relationship.newBuilder()
            .setResource(
                ObjectReference.newBuilder()
                    .setType(ResourceType.INTEGRATION.getKesselObjectType())
                    .setId(endpoint.getId().toString())
                    .build()
            ).setRelation(RELATION)
            .setSubject(
                SubjectReference.newBuilder()
                    .setSubject(
                        ObjectReference.newBuilder()
                            .setType(ObjectType.newBuilder().setNamespace("rbac").setName("workspace").build())
                            .setId(this.workspaceUtils.getDefaultWorkspaceId(endpoint.getOrgId()).toString())
                            .build()
                    ).build()
            ).build();
    }
}
