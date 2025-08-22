package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.Sort;
import com.redhat.cloud.notifications.models.CompositeEndpointType;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.HttpType;
import com.redhat.cloud.notifications.models.SystemSubscriptionProperties;
import com.redhat.cloud.notifications.models.WebhookProperties;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
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
            resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, orgId, EndpointType.DRAWER, null, "7", NOT_USED, null, true),
            resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, orgId, EndpointType.PAGERDUTY, null, "8", NOT_USED, null, true)
        )
                // In java 17 - my system retrieves a created field (LocalDateTime) with higher precision of what's really stored - load the data for the sake of the test.
                .map(endpoint -> endpointRepository.getEndpoint(endpoint.getOrgId(), endpoint.getId())).toList();

        Set<CompositeEndpointType> compositeEndpointTypes = Set.of(
                CompositeEndpointType.fromString("camel"),
                CompositeEndpointType.fromString("webhook"),
                CompositeEndpointType.fromString("email_subscription"),
                CompositeEndpointType.fromString("drawer"),
                CompositeEndpointType.fromString("pagerduty")
        );

        Function<Query, List<Endpoint>> provider = query -> endpointRepository.getEndpointsPerCompositeType(orgId, null, compositeEndpointTypes, null, query, null);
        TestHelpers.testSorting(
                "id",
                provider,
                endpoints -> endpoints.stream().map(Endpoint::getId).collect(Collectors.toList()),
                Sort.Order.ASC,
                createdEndpointList.stream().map(Endpoint::getId).map(UUID::toString).sorted().map(UUID::fromString).collect(Collectors.toList())
        );

        TestHelpers.testSorting(
                "name",
                provider,
                endpoints -> endpoints.stream().map(Endpoint::getName).collect(Collectors.toList()),
                Sort.Order.ASC,
                createdEndpointList.stream().map(Endpoint::getName).sorted().collect(Collectors.toList())
        );

        TestHelpers.testSorting(
                "enabled",
                provider,
                endpoints -> endpoints.stream().map(Endpoint::isEnabled).collect(Collectors.toList()),
                Sort.Order.ASC,
                List.of(Boolean.FALSE, Boolean.FALSE, Boolean.FALSE, Boolean.TRUE, Boolean.TRUE, Boolean.TRUE, Boolean.TRUE, Boolean.TRUE)
        );

        TestHelpers.testSorting(
                "type",
                provider,
                endpoints -> endpoints.stream().map(Endpoint::getType).collect(Collectors.toList()),
                Sort.Order.ASC,
                List.of(EndpointType.CAMEL, EndpointType.CAMEL, EndpointType.CAMEL, EndpointType.CAMEL, EndpointType.DRAWER, EndpointType.EMAIL_SUBSCRIPTION, EndpointType.PAGERDUTY, EndpointType.WEBHOOK)
        );

        TestHelpers.testSorting(
                "created",
                provider,
                endpoints -> endpoints.stream().map(Endpoint::getCreated).collect(Collectors.toList()),
                Sort.Order.ASC,
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
     * Tests that when the user does not have authorization to fetch any
     * endpoints, then none are fetched.
     */
    @Test
    void testShouldNotFetchEndpointWhenUnauthorized() {
        // Create a few endpoints.
        final List<Endpoint> createdEndpoints = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            createdEndpoints.add(
                this.resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, EndpointType.WEBHOOK)
            );
        }

        // Call the function under test.
        final List<Endpoint> fetchedEndpoints = this.endpointRepository.getEndpointsPerCompositeType(
            DEFAULT_ORG_ID,
            null,
            Set.of(new CompositeEndpointType(EndpointType.WEBHOOK)),
            null,
            null,
            new HashSet<>()
        );

        // Call the count function under test.
        final Long countedEndpoints = this.endpointRepository.getEndpointsCountPerCompositeType(
            DEFAULT_ORG_ID,
            null,
            Set.of(new CompositeEndpointType(EndpointType.WEBHOOK)),
            null,
            new HashSet<>()
        );

        // Assert that no endpoints were fetched.
        Assertions.assertTrue(fetchedEndpoints.isEmpty(), "even though no authorized IDs were specified, at least one endpoint was fetched from the database");
        Assertions.assertEquals(0, countedEndpoints, "even though no authorized IDs were specified, at least one endpoint was counted in the database");
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

    /**
     * Tests that the {@link EndpointRepository#getNonSystemEndpointsByOrgIdWithLimitAndOffset(Optional, int, int)}
     * function only fetches regular endpoints, and that it respects the
     * "limit" and "offset" values.
     */
    @Test
    void testGetNonSystemEndpointsWithLimitAndOffset() {
        resourceHelpers.deleteEndpoints();
        // Create a few regular and migratable endpoints.
        final int regularEndpointsToCreate = 10;
        final ArrayList<Endpoint> createdEndpoints = new ArrayList<>(regularEndpointsToCreate);
        final Random random = new Random();
        for (int i = 0; i < regularEndpointsToCreate; i++) {
            final WebhookProperties webhookProperties = new WebhookProperties();
            webhookProperties.setDisableSslVerification(random.nextBoolean());
            webhookProperties.setMethod(HttpType.GET);
            webhookProperties.setUrl("https://redhat.com");

            final Endpoint createdEndpoint = this.resourceHelpers.createEndpoint(
                DEFAULT_ACCOUNT_ID,
                DEFAULT_ORG_ID,
                EndpointType.WEBHOOK,
                null,
                String.format("Webhook integration %s", i),
                String.format("Webhook integration %s", i),
                webhookProperties,
                true,
                LocalDateTime.now(ZoneOffset.UTC).minusDays(i)
            );

            createdEndpoints.add(createdEndpoint);
        }

        // Create some integrations in a different organization.
        for (int i = 0; i < regularEndpointsToCreate; i++) {
            final WebhookProperties webhookProperties = new WebhookProperties();
            webhookProperties.setDisableSslVerification(random.nextBoolean());
            webhookProperties.setMethod(HttpType.GET);
            webhookProperties.setUrl("https://redhat.com");

            final Endpoint createdEndpoint = this.resourceHelpers.createEndpoint(
                DEFAULT_ACCOUNT_ID + "other",
                DEFAULT_ORG_ID + "other",
                EndpointType.WEBHOOK,
                null,
                String.format("Webhook integration %s", i),
                String.format("Webhook integration %s", i),
                webhookProperties,
                true,
                LocalDateTime.now(ZoneOffset.UTC).minusDays(i)
            );

            createdEndpoints.add(createdEndpoint);
        }

        // Create a few system endpoints.
        for (int i = 0; i < 5; i++) {
            final SystemSubscriptionProperties properties = new SystemSubscriptionProperties();
            properties.setOnlyAdmins(random.nextBoolean());

            this.resourceHelpers.createEndpoint(
                null,
                null,
                EndpointType.EMAIL_SUBSCRIPTION,
                null,
                "Email endpoint",
                "System email endpoint",
                properties,
                true,
                LocalDateTime.now()
            );
        }

        // Call the function under test.
        final List<Endpoint> fetchedEndpoints = this.endpointRepository.getNonSystemEndpointsByOrgIdWithLimitAndOffset(Optional.of(DEFAULT_ORG_ID), 50, 0);
        Assertions.assertEquals(regularEndpointsToCreate, fetchedEndpoints.size(), "unexpected number of endpoints fetched");

        // Make sure all the endpoints are non system endpoints.
        fetchedEndpoints.forEach(
            endpoint -> Assertions.assertFalse(
                EndpointType.DRAWER.equals(endpoint.getType()) || EndpointType.EMAIL_SUBSCRIPTION.equals(endpoint.getType()),
                "fetched a system endpoint type when the function under test should not have fetched any"
            )
        );

        // Call the function under test with a limit.
        final List<Endpoint> limitedEndpoints = this.endpointRepository.getNonSystemEndpointsByOrgIdWithLimitAndOffset(Optional.of(DEFAULT_ORG_ID), 1, 0);
        Assertions.assertEquals(1, limitedEndpoints.size(), "only one endpoint should have been fetched. The limit option has not been respected");

        // Call the function under test with an offset. Reverse the created
        // endpoints so that the "oldest" one goes first in the list.+
        final List<Endpoint> reversedEndpoints = createdEndpoints.reversed();
        final Iterator<Endpoint> expectedEndpoints = reversedEndpoints.iterator();
        for (int i = 0; i < regularEndpointsToCreate; i++) {
            final List<Endpoint> offsetedEndpoint = this.endpointRepository.getNonSystemEndpointsByOrgIdWithLimitAndOffset(Optional.of(DEFAULT_ORG_ID), 1, i);
            Assertions.assertEquals(1, offsetedEndpoint.size(), "only a single endpoint should hav been fetched since the limit is set to \"1\"");

            // Compare the fetched endpoint from the database with the one we
            // expect. ChronoUnit is used because the nanoseconds get trimmed
            // when getting stored in the database, so technically both
            // instants are not the same, but we know they are.
            final Endpoint expectedEndpoint = expectedEndpoints.next();
            Assertions.assertEquals(0, ChronoUnit.DAYS.between(expectedEndpoint.getCreated(), offsetedEndpoint.getFirst().getCreated()), String.format("the function under test should have fetched an endpoint created \"%s\" days ago", i));
        }

        // When applied the offset "0", we should get the oldest integration by
        // default due to the "order by" clause.
        final List<Endpoint> oldestEndpoint = this.endpointRepository.getNonSystemEndpointsByOrgIdWithLimitAndOffset(Optional.of(DEFAULT_ORG_ID), 1, 0);
        Assertions.assertEquals(0, ChronoUnit.DAYS.between(reversedEndpoints.getFirst().getCreated(), oldestEndpoint.getFirst().getCreated()), "when the offset is \"0\", the oldest endpoint should have been fetched from the database");

        // When applied the highest offset, the newest endpoint should have
        // been fetched due to the "order by" clause.
        final List<Endpoint> newestEndpoint = this.endpointRepository.getNonSystemEndpointsByOrgIdWithLimitAndOffset(Optional.of(DEFAULT_ORG_ID), 1, regularEndpointsToCreate - 1);
        Assertions.assertEquals(0, ChronoUnit.DAYS.between(reversedEndpoints.getLast().getCreated(), newestEndpoint.getFirst().getCreated()), "when the offset is the highest one, the newest endpoint should have been fetched from the database");
    }
}
