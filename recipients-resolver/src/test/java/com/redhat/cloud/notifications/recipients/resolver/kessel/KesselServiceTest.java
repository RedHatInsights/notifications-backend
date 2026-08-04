package com.redhat.cloud.notifications.recipients.resolver.kessel;

import com.redhat.cloud.notifications.ingress.RecipientsAuthorizationCriterion;
import com.redhat.cloud.notifications.ingress.Type;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.project_kessel.api.auth.OAuth2Exception;
import org.project_kessel.api.inventory.v1beta2.RepresentationType;
import org.project_kessel.api.inventory.v1beta2.ResourceReference;
import org.project_kessel.api.inventory.v1beta2.StreamedListSubjectsRequest;
import org.project_kessel.api.inventory.v1beta2.StreamedListSubjectsResponse;
import org.project_kessel.api.inventory.v1beta2.SubjectReference;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class KesselServiceTest {

    @Inject
    KesselService kesselService;

    @InjectMock
    KesselInventoryClient kesselInventoryClient;

    private static RecipientsAuthorizationCriterion buildCriterion() {
        Type type = new Type();
        type.setNamespace("namespace_test");
        type.setName("host");

        RecipientsAuthorizationCriterion criterion = new RecipientsAuthorizationCriterion();
        criterion.setId("resource-id-1");
        criterion.setRelation("relationship");
        criterion.setType(type);
        return criterion;
    }

    private static StreamedListSubjectsResponse buildResponse(String subjectResourceId) {
        return StreamedListSubjectsResponse.newBuilder()
            .setSubject(SubjectReference.newBuilder()
                .setResource(ResourceReference.newBuilder()
                    .setResourceId(subjectResourceId)
                    .build())
                .build())
            .build();
    }

    private static Iterator<StreamedListSubjectsResponse> throwingIterator(RuntimeException exception) {
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                throw exception;
            }

            @Override
            public StreamedListSubjectsResponse next() {
                throw new NoSuchElementException();
            }
        };
    }

    @Test
    void testLookupSubjectsBuildsRequestFromCriterion() {
        when(kesselInventoryClient.streamedListSubjects(any())).thenReturn(List.<StreamedListSubjectsResponse>of().iterator());

        kesselService.lookupSubjects(buildCriterion());

        ArgumentCaptor<StreamedListSubjectsRequest> captor = ArgumentCaptor.forClass(StreamedListSubjectsRequest.class);
        verify(kesselInventoryClient).streamedListSubjects(captor.capture());
        StreamedListSubjectsRequest request = captor.getValue();

        assertEquals("namespace_test", request.getResource().getReporter().getType());
        assertEquals("host", request.getResource().getResourceType());
        assertEquals("resource-id-1", request.getResource().getResourceId());
        assertEquals("relationship", request.getRelation());
        assertEquals(RepresentationType.newBuilder().setReporterType("rbac").setResourceType("principal").build(), request.getSubjectType());
    }

    @Test
    void testLookupSubjectsStripsDomainPrefixFromUserIds() {
        when(kesselInventoryClient.streamedListSubjects(any())).thenReturn(List.of(
            buildResponse("redhat/userId1"),
            buildResponse("redhat/userId2")
        ).iterator());

        Set<String> userIds = kesselService.lookupSubjects(buildCriterion());

        assertEquals(Set.of("userId1", "userId2"), userIds);
    }

    @Test
    void testLookupSubjectsRetriesWholeStreamOnTransientError() {
        StatusRuntimeException transientError = new StatusRuntimeException(Status.UNAVAILABLE);
        when(kesselInventoryClient.streamedListSubjects(any()))
            .thenReturn(throwingIterator(transientError))
            .thenReturn(List.of(buildResponse("redhat/userId1")).iterator());
        when(kesselInventoryClient.handleGrpcException(any())).thenReturn(new KesselTransientException(transientError));

        Set<String> userIds = kesselService.lookupSubjects(buildCriterion());

        assertEquals(Set.of("userId1"), userIds);
        verify(kesselInventoryClient, times(2)).streamedListSubjects(any());
    }

    @Test
    void testLookupSubjectsRetriesOnOAuth2DiscoveryFailure() {
        // Simulates initializeChannel()'s synchronous OIDC-discovery call failing on a channel
        // reinit -- an OAuth2Exception, not a StatusRuntimeException, since no gRPC call was made yet.
        OAuth2Exception discoveryFailure = new OAuth2Exception("OIDC issuer unreachable");
        when(kesselInventoryClient.streamedListSubjects(any()))
            .thenThrow(discoveryFailure)
            .thenReturn(List.of(buildResponse("redhat/userId1")).iterator());

        Set<String> userIds = kesselService.lookupSubjects(buildCriterion());

        assertEquals(Set.of("userId1"), userIds);
        verify(kesselInventoryClient, times(2)).streamedListSubjects(any());
    }

    @Test
    void testLookupSubjectsExhaustsRetriesOnPersistentOAuth2DiscoveryFailure() {
        OAuth2Exception discoveryFailure = new OAuth2Exception("OIDC issuer unreachable");
        when(kesselInventoryClient.streamedListSubjects(any())).thenThrow(discoveryFailure);

        assertThrows(KesselTransientException.class, () -> kesselService.lookupSubjects(buildCriterion()));

        // 1 initial call + 3 retries = 4 total calls
        verify(kesselInventoryClient, times(4)).streamedListSubjects(any());
    }

    @Test
    void testLookupSubjectsDoesNotRetryOnNonTransientError() {
        StatusRuntimeException permissionDenied = new StatusRuntimeException(Status.PERMISSION_DENIED);
        when(kesselInventoryClient.streamedListSubjects(any())).thenReturn(throwingIterator(permissionDenied));
        when(kesselInventoryClient.handleGrpcException(eq(permissionDenied))).thenReturn(permissionDenied);

        assertThrows(StatusRuntimeException.class, () -> kesselService.lookupSubjects(buildCriterion()));

        verify(kesselInventoryClient, times(1)).streamedListSubjects(any());
    }

    @Test
    void testLookupSubjectsExhaustsRetriesOnPersistentTransientError() {
        StatusRuntimeException transientError = new StatusRuntimeException(Status.UNAVAILABLE);
        when(kesselInventoryClient.streamedListSubjects(any())).thenReturn(throwingIterator(transientError));
        when(kesselInventoryClient.handleGrpcException(any())).thenReturn(new KesselTransientException(transientError));

        assertThrows(KesselTransientException.class, () -> kesselService.lookupSubjects(buildCriterion()));

        // 1 initial call + 3 retries = 4 total calls
        verify(kesselInventoryClient, times(4)).streamedListSubjects(any());
    }
}
