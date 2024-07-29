package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.CompositeEndpointType;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@QuarkusTest
public class EndpointRepositoryTest {

    private static final String NOT_USED = "not-used";

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    EndpointRepository endpointRepository;

    @Test
    void shouldSortCorrectly() {
        String orgId = "endpoint-repository-test-sort";

        List<Endpoint> createdEndpointList = Stream.of(
            resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, orgId, EndpointType.CAMEL, NOT_USED, "1", NOT_USED, null, true),
            resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, orgId, EndpointType.WEBHOOK, null, "2", NOT_USED, null, true),
            resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, orgId, EndpointType.EMAIL_SUBSCRIPTION, null, "3", NOT_USED, null, true),
            resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, orgId, EndpointType.CAMEL, NOT_USED, "4", NOT_USED, null, false),
            resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, orgId, EndpointType.CAMEL, NOT_USED, "5", NOT_USED, null, false),
            resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, orgId, EndpointType.CAMEL, NOT_USED, "6", NOT_USED, null, false),
            resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, orgId, EndpointType.DRAWER, null, "7", NOT_USED, null, true)
        )
                // In java 17 - my system retrieves a created field (LocalDateTime) with higher precision of what's really stored - load the data for the sake of the test.
                .map(endpoint -> endpointRepository.getEndpoint(endpoint.getOrgId(), endpoint.getId())).toList();

        Set<CompositeEndpointType> compositeEndpointTypes = Set.of(
                CompositeEndpointType.fromString("camel"),
                CompositeEndpointType.fromString("webhook"),
                CompositeEndpointType.fromString("email_subscription"),
                CompositeEndpointType.fromString("drawer")
        );

        Function<Query, List<Endpoint>> provider = query -> endpointRepository.getEndpointsPerCompositeType(orgId, null, compositeEndpointTypes, null, query, null);
        TestHelpers.testSorting(
                "id",
                provider,
                endpoints -> endpoints.stream().map(Endpoint::getId).collect(Collectors.toList()),
                Query.Sort.Order.ASC,
                createdEndpointList.stream().map(Endpoint::getId).map(UUID::toString).sorted().map(UUID::fromString).collect(Collectors.toList())
        );

        TestHelpers.testSorting(
                "name",
                provider,
                endpoints -> endpoints.stream().map(Endpoint::getName).collect(Collectors.toList()),
                Query.Sort.Order.ASC,
                createdEndpointList.stream().map(Endpoint::getName).sorted().collect(Collectors.toList())
        );

        TestHelpers.testSorting(
                "enabled",
                provider,
                endpoints -> endpoints.stream().map(Endpoint::isEnabled).collect(Collectors.toList()),
                Query.Sort.Order.ASC,
                List.of(Boolean.FALSE, Boolean.FALSE, Boolean.FALSE, Boolean.TRUE, Boolean.TRUE, Boolean.TRUE, Boolean.TRUE)
        );

        TestHelpers.testSorting(
                "type",
                provider,
                endpoints -> endpoints.stream().map(Endpoint::getType).collect(Collectors.toList()),
                Query.Sort.Order.ASC,
                List.of(EndpointType.CAMEL, EndpointType.CAMEL, EndpointType.CAMEL, EndpointType.CAMEL, EndpointType.DRAWER, EndpointType.EMAIL_SUBSCRIPTION, EndpointType.WEBHOOK)
        );

        TestHelpers.testSorting(
                "created",
                provider,
                endpoints -> endpoints.stream().map(Endpoint::getCreated).collect(Collectors.toList()),
                Query.Sort.Order.ASC,
                createdEndpointList.stream().map(Endpoint::getCreated).sorted().collect(Collectors.toList())
        );
    }

    @Test
    void queryBuilderTest() {
        TypedQuery<Endpoint> query = mock(TypedQuery.class);

        // types with subtype and without it
        EndpointRepository.queryBuilderEndpointsPerType(
                null,
                null,
                Set.of(
                        new CompositeEndpointType(EndpointType.WEBHOOK),
                        new CompositeEndpointType(EndpointType.CAMEL, "splunk")
                ),
                null,
                null
        ).build((hql, endpointClass) -> {
            assertEquals("SELECT e FROM Endpoint e WHERE e.orgId IS NULL AND (e.compositeType.type IN (:endpointType) OR e.compositeType IN (:compositeTypes))", hql);
            return query;
        });

        verify(query, times(2)).setParameter((String) any(), any());
        verifyNoMoreInteractions(query);
        clearInvocations(query);

        // without sub-types
        EndpointRepository.queryBuilderEndpointsPerType(
                null,
                null,
                Set.of(
                        new CompositeEndpointType(EndpointType.WEBHOOK)
                ),
                null,
                null
        ).build((hql, endpointClass) -> {
            assertEquals("SELECT e FROM Endpoint e WHERE e.orgId IS NULL AND (e.compositeType.type IN (:endpointType))", hql);
            return query;
        });

        verify(query, times(1)).setParameter((String) any(), any());
        verifyNoMoreInteractions(query);
        clearInvocations(query);

        // with sub-types
        EndpointRepository.queryBuilderEndpointsPerType(
                null,
                null,
                Set.of(
                        new CompositeEndpointType(EndpointType.CAMEL, "splunk")
                ),
                null,
                null
        ).build((hql, endpointClass) -> {
            assertEquals("SELECT e FROM Endpoint e WHERE e.orgId IS NULL AND (e.compositeType IN (:compositeTypes))", hql);
            return query;
        });

        verify(query, times(1)).setParameter((String) any(), any());
        verifyNoMoreInteractions(query);
        clearInvocations(query);
    }

    /**
     * Tests that an endpoint is successfully found in the database with the
     * function under test.
     */
    @Test
    void endpointExistsByUuidAndOrgId() {
        final String orgId = "endpoint-exists-by-uuid-and-org-id";
        final Endpoint createdEndpoint = this.resourceHelpers.createEndpoint("account-id", orgId, EndpointType.CAMEL);

        Assertions.assertTrue(this.endpointRepository.existsByUuidAndOrgId(createdEndpoint.getId(), orgId), "the just created endpoint wasn't found by the exists by UUID and OrgId query");
    }

    /**
     * Tests that the function under test will return "false" if an endpoint
     * is not found by its UUID and OrgId.
     */
    @Test
    void endpointDoesntExistByUuidAndOrgId() {
        Assertions.assertFalse(this.endpointRepository.existsByUuidAndOrgId(UUID.randomUUID(), "random-org-id"), "an endpoint was found by its UUID in the database, though it wasn't expected");
    }

    /**
     * Tests that the function under test will return "false" if an existing
     * UUID is provided but with a different org id.
     */
    @Test
    void endpointDoesntExistByUuidAndIncorrectOrgId() {
        final String orgId = "endpoint-exists-by-uuid-and-incorrect-org-id";
        final Endpoint createdEndpoint = this.resourceHelpers.createEndpoint("account-id", orgId, EndpointType.CAMEL);

        Assertions.assertFalse(this.endpointRepository.existsByUuidAndOrgId(createdEndpoint.getId(), "incorrect-org-id"));
    }

    /**
     * Tests that when the function under test is given a set of identifiers
     * for the endpoints that the user is allowed to fetch, it respects it and
     * just fetches those endpoints.
     */
    @Test
    void testShouldOnlyFetchAuthorizedIntegrations() {
        // Create a few endpoints.
        final List<Endpoint> createdEndpoints = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            createdEndpoints.add(
                this.resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, EndpointType.WEBHOOK)
            );
        }

        // Simulate that we only have authorization to fetch the first and the
        // last created endpoints.
        final Set<UUID> authorizedIds = new HashSet<>();
        authorizedIds.add(createdEndpoints.getFirst().getId());
        authorizedIds.add(createdEndpoints.getLast().getId());

        // Call the function under test.
        final List<Endpoint> fetchedEndpoints = this.endpointRepository.getEndpointsPerCompositeType(
            DEFAULT_ORG_ID,
            null,
            Set.of(new CompositeEndpointType(EndpointType.WEBHOOK)),
            null,
            null,
            authorizedIds
        );

        // Call the "count" function to test it too, since we are at it.
        final Long countedEndpoints = this.endpointRepository.getEndpointsCountPerCompositeType(
            DEFAULT_ORG_ID,
            null,
            Set.of(new CompositeEndpointType(EndpointType.WEBHOOK)),
            null,
            authorizedIds
        );

        // Assert that the count is correct both for the returned result from
        // the second function under test and the number of endpoints returned
        // from the first one.
        Assertions.assertEquals(authorizedIds.size(), countedEndpoints, "the \"getEndpointsCountPerCompositeType\" function did not filter the result by the authorized IDs");
        Assertions.assertEquals(authorizedIds.size(), fetchedEndpoints.size(), "the \"getEndpointsPerCompositeType\" function did not filter the result by the authorized IDs");

        // Assert that the fetched endpoints are just the authorized ones.
        fetchedEndpoints.forEach(endpoint -> Assertions.assertTrue(authorizedIds.contains(endpoint.getId()), "the \"getEndpointsPerCompositeType\" function fetched an endpoint which we were not authorized to fetch"));
    }
}
