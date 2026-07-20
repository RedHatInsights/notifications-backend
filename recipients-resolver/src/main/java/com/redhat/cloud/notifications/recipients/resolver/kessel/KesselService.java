package com.redhat.cloud.notifications.recipients.resolver.kessel;

import com.redhat.cloud.notifications.ingress.RecipientsAuthorizationCriterion;
import com.redhat.cloud.notifications.recipients.config.RecipientsResolverConfig;
import io.grpc.StatusRuntimeException;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.project_kessel.api.auth.OAuth2Exception;
import org.project_kessel.api.inventory.v1beta2.ReporterReference;
import org.project_kessel.api.inventory.v1beta2.RepresentationType;
import org.project_kessel.api.inventory.v1beta2.ResourceReference;
import org.project_kessel.api.inventory.v1beta2.StreamedListSubjectsRequest;
import org.project_kessel.api.inventory.v1beta2.StreamedListSubjectsResponse;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

@ApplicationScoped
public class KesselService {

    static final String SUBJECT_TYPE_USER = "principal";

    static final String RBAC_NAMESPACE = "rbac";

    @Inject
    RecipientsResolverConfig recipientsResolverConfig;

    @Inject
    KesselInventoryClient kesselInventoryClient;

    // worst-case retry budget (4 x kessel.timeout-ms = 120s) exceeds callers' default 30s REST timeout.
    // Confirmed: none of engine/connector-email/connector-drawer override quarkus.rest-client.recipients-resolver's
    // read-timeout, so all three genuinely get Quarkus's 30s default. Also, this budget isn't competing with an
    // otherwise-empty request: RecipientsResolver.findRecipients() calls this lookup first, then still has to run
    // RBAC/MBOP calls (FetchUsersFromExternalServices) per RecipientSettings in the same request/timeout window --
    // so the safe target is meaningfully less than 30s, not just-under-30s. No real traffic exercises this path yet
    // (use-kessel toggle is off everywhere), so there's no latency data to size against. Needs sizing guidance.
    @Retry(maxRetries = 3, delay = 100, retryOn = KesselTransientException.class)
    public Set<String> lookupSubjects(RecipientsAuthorizationCriterion recipientsAuthorizationCriterion) {
        StreamedListSubjectsRequest request = getStreamedListSubjectsRequest(recipientsAuthorizationCriterion);

        final String kesselAdditionalDomainName = String.format("%s/", recipientsResolverConfig.getKesselDomain());
        Set<String> userIds = new HashSet<>();
        try {
            for (Iterator<StreamedListSubjectsResponse> it = kesselInventoryClient.streamedListSubjects(request); it.hasNext();) {
                StreamedListSubjectsResponse response = it.next();
                userIds.add(response.getSubject().getResource().getResourceId().replace(kesselAdditionalDomainName, ""));
            }
        } catch (StatusRuntimeException e) {
            throw kesselInventoryClient.handleGrpcException(e);
        } catch (OAuth2Exception e) {
            Log.warnf("Transient error fetching Kessel OAuth2 credentials (may retry): %s", e.getMessage());
            throw new KesselTransientException(e);
        }
        Log.infof("Kessel returned %d user(s) for request %s", userIds.size(), request);
        return userIds;
    }

    private static StreamedListSubjectsRequest getStreamedListSubjectsRequest(RecipientsAuthorizationCriterion recipientsAuthorizationCriterion) {
        return StreamedListSubjectsRequest.newBuilder()
            .setResource(ResourceReference.newBuilder()
                .setReporter(ReporterReference.newBuilder()
                    .setType(recipientsAuthorizationCriterion.getType().getNamespace()).build())
                .setResourceType(recipientsAuthorizationCriterion.getType().getName())
                .setResourceId(recipientsAuthorizationCriterion.getId())
                .build())
            .setRelation(recipientsAuthorizationCriterion.getRelation())
            .setSubjectType(RepresentationType.newBuilder().setReporterType(RBAC_NAMESPACE).setResourceType(SUBJECT_TYPE_USER).build())
            .build();
    }
}
