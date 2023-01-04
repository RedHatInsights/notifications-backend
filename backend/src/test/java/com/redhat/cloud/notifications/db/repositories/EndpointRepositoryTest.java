package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.CompositeEndpointType;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.persistence.TypedQuery;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
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

    // @Test
    void shouldSortCorrectly() {
        String orgId = "endpoint-repository-test-sort";

        List<Endpoint> createdEndpointList = Stream.of(
            resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, orgId, EndpointType.CAMEL, NOT_USED, "1", NOT_USED, null, true),
            resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, orgId, EndpointType.WEBHOOK, null, "2", NOT_USED, null, true),
            resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, orgId, EndpointType.EMAIL_SUBSCRIPTION, null, "3", NOT_USED, null, true),
            resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, orgId, EndpointType.CAMEL, NOT_USED, "4", NOT_USED, null, false),
            resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, orgId, EndpointType.CAMEL, NOT_USED, "5", NOT_USED, null, false),
            resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, orgId, EndpointType.CAMEL, NOT_USED, "6", NOT_USED, null, false)
        )
                // In java 17 - my system retrieves a created field (LocalDateTime) with higher precision of what's really stored - load the data for the sake of the test.
                .map(endpoint -> endpointRepository.getEndpoint(endpoint.getOrgId(), endpoint.getId())).toList();

        Set<CompositeEndpointType> compositeEndpointTypes = Set.of(
                CompositeEndpointType.fromString("camel"),
                CompositeEndpointType.fromString("webhook"),
                CompositeEndpointType.fromString("email_subscription")
        );

        Function<Query, List<Endpoint>> provider = query -> endpointRepository.getEndpointsPerCompositeType(orgId, null, compositeEndpointTypes, null, query);
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
                List.of(Boolean.FALSE, Boolean.FALSE, Boolean.FALSE, Boolean.TRUE, Boolean.TRUE, Boolean.TRUE)
        );

        TestHelpers.testSorting(
                "type",
                provider,
                endpoints -> endpoints.stream().map(Endpoint::getType).collect(Collectors.toList()),
                Query.Sort.Order.ASC,
                List.of(EndpointType.CAMEL, EndpointType.CAMEL, EndpointType.CAMEL, EndpointType.CAMEL, EndpointType.EMAIL_SUBSCRIPTION, EndpointType.WEBHOOK)
        );

        TestHelpers.testSorting(
                "created",
                provider,
                endpoints -> endpoints.stream().map(Endpoint::getCreated).collect(Collectors.toList()),
                Query.Sort.Order.ASC,
                createdEndpointList.stream().map(Endpoint::getCreated).sorted()
                        // on my system - and when running from the command line - the database return this truncated to MICROS - but they get created up to the NANO seconds
                        .map(c -> c.truncatedTo(ChronoUnit.MICROS))
                        .collect(Collectors.toList())
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
}
