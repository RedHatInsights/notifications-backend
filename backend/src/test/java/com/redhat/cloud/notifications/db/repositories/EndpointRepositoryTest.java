package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.CompositeEndpointType;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.persistence.TypedQuery;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    @Test
    void shouldSortCorrectly() {
        String orgId = "endpoint-repository-test-sort";

        List<Endpoint> createdEndpointList = List.of(
            resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, orgId, EndpointType.CAMEL, NOT_USED, "1", NOT_USED, null, true),
            resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, orgId, EndpointType.WEBHOOK, NOT_USED, "2", NOT_USED, null, true),
            resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, orgId, EndpointType.EMAIL_SUBSCRIPTION, NOT_USED, "3", NOT_USED, null, true),
            resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, orgId, EndpointType.CAMEL, NOT_USED, "4", NOT_USED, null, false),
            resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, orgId, EndpointType.CAMEL, NOT_USED, "5", NOT_USED, null, false),
            resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, orgId, EndpointType.CAMEL, NOT_USED, "6", NOT_USED, null, false)
        );

        Set<CompositeEndpointType> compositeEndpointTypes = Set.of(
                CompositeEndpointType.fromString("camel"),
                CompositeEndpointType.fromString("webhook"),
                CompositeEndpointType.fromString("email_subscription")
        );

        List<Function<Query, List<Endpoint>>> testProviders = List.of(
                query -> endpointRepository.getEndpoints(orgId, null, query),
                query -> endpointRepository.getEndpointsPerCompositeType(orgId, null, compositeEndpointTypes, null, query)
        );

        for (Function<Query, List<Endpoint>> provider : testProviders) {
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
                    List.of(EndpointType.WEBHOOK, EndpointType.EMAIL_SUBSCRIPTION, EndpointType.CAMEL, EndpointType.CAMEL, EndpointType.CAMEL, EndpointType.CAMEL)
            );

            TestHelpers.testSorting(
                    "created",
                    provider,
                    endpoints -> endpoints.stream().map(Endpoint::getCreated).collect(Collectors.toList()),
                    Query.Sort.Order.ASC,
                    createdEndpointList.stream().map(Endpoint::getCreated).sorted().collect(Collectors.toList())
            );
        }
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

}
