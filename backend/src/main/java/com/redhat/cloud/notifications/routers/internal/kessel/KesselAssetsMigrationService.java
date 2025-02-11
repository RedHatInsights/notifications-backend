package com.redhat.cloud.notifications.routers.internal.kessel;

import com.redhat.cloud.notifications.auth.ConsoleIdentityProvider;
import com.redhat.cloud.notifications.auth.kessel.ResourceType;
import com.redhat.cloud.notifications.auth.rbac.workspace.WorkspaceUtils;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.models.Endpoint;
import io.grpc.stub.StreamObserver;
import io.quarkus.logging.Log;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nullable;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
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
import java.util.Optional;
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

    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/kessel/migrate-assets")
    @POST
    @RunOnVirtualThread
    public void migrateAssets(@Nullable final KesselAssetsMigrationRequest kamRequest) {
        Log.info("Kessel assets' migration begins");

        // Grab the organization ID specified in the request.
        final Optional<String> orgId = (kamRequest == null) ? Optional.empty() : Optional.of(kamRequest.orgId());

        int fetchedEndpointsSize = 0;
        int offset = 0;
        int traceLoops = 0;
        do {
            Log.debugf("[loops: %s] Loops", traceLoops);

            final List<Endpoint> fetchedEndpoints = this.endpointRepository.getNonSystemEndpointsByOrgIdWithLimitAndOffset(orgId, this.backendConfig.getKesselMigrationBatchSize(), offset);
            Log.debugf("[offset: %s][first_integration: %s][last_integration: %s] Fetched batch of %s integrations", offset, (fetchedEndpoints.isEmpty()) ? "none" : fetchedEndpoints.getFirst().getId(), (fetchedEndpoints.isEmpty()) ? "none" : fetchedEndpoints.getLast().getId(), fetchedEndpoints.size());

            // If for some reason we have fetched full pages from the database
            // all the time, the last one might be empty, so there is no need
            // to attempt calling Kessel.
            if (fetchedEndpoints.isEmpty()) {
                Log.debug("Breaking the do-while loop because the size of the fetched integrations is zero");
                break;
            }

            final CreateTuplesRequest request = this.createTuplesRequest(fetchedEndpoints);
            Log.tracef("Generated a \"CreateTuplesRequest\": %s", request);

            final int finalOffset = offset;
            this.relationTuplesClient.createTuples(request, new StreamObserver<>() {
                @Override
                public void onNext(final CreateTuplesResponse createTuplesResponse) {
                    Log.debug("Calling onNext");
                    Log.infof("[offset: %s][first_integration: %s][last_integration: %s] Sent batch of %s integrations to Kessel", finalOffset, fetchedEndpoints.getFirst().getId(), fetchedEndpoints.getLast().getId(), fetchedEndpoints.size());
                }

                @Override
                public void onError(final Throwable throwable) {
                    Log.debug("Calling onError");
                    Log.errorf(throwable, "[offset: %s][first_integration: %s][last_integration: %s] Unable to send batch of tuples to Kessel", finalOffset, fetchedEndpoints.getFirst().getId(), fetchedEndpoints.getLast().getId());
                }

                @Override
                public void onCompleted() {
                    Log.debug("Calling onCompleted");
                    Log.infof("[offset: %s][first_integration: %s][last_integration: %s] Sent batch of %s integrations to Kessel", finalOffset, fetchedEndpoints.getFirst().getId(), fetchedEndpoints.getLast().getId(), fetchedEndpoints.size());
                }
            });

            fetchedEndpointsSize = fetchedEndpoints.size();
            offset += fetchedEndpoints.size();
            traceLoops += 1;

            Log.debugf("[fetchedEndpointsSize: %s][kesselMigrationBatchSize: %s][offset: %s] do-while loop condition", fetchedEndpointsSize, offset, this.backendConfig.getKesselMigrationBatchSize());
        } while (fetchedEndpointsSize == this.backendConfig.getKesselMigrationBatchSize());

        Log.info("Finished migrating integrations to the Kessel inventory");
    }

    @Path("/kessel/migrate-assets/async")
    @POST
    public void migrateAssetsAsync(final Optional<String> orgId) {
        Log.info("Kessel assets' migration begins");

        final int limit = this.backendConfig.getKesselMigrationBatchSize();

        final AtomicInteger offsetContainer = new AtomicInteger(0);
        final Multi<List<Endpoint>> integrationBatch = Multi
            .createBy()
            .repeating()
            .supplier(
                () -> offsetContainer.addAndGet(limit),
                offset -> {
                    final List<Endpoint> endpoints = this.endpointRepository.getNonSystemEndpointsByOrgIdWithLimitAndOffset(orgId, limit, offset);
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
            } catch (final Exception e) {
                Log.errorf("[org_id: %s][endpoint_id: %s] Unable to get the default workspace for integration", endpoint.getOrgId(), endpoint.getId());
            }
        }

        return CreateTuplesRequest
            .newBuilder()
            .addAllTuples(relations)
            .setUpsert(true)
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
