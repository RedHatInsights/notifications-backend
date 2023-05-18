package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.BasicAuthentication;
import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.CompositeEndpointType;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.HttpType;
import com.redhat.cloud.notifications.models.WebhookProperties;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.persistence.TypedQuery;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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

    /**
     * Tests that the function under test only picks up the endpoints that have
     * basic authentication or secret token secrets that don't have the
     * corresponding Sources' reference in the table.
     */
    @Deprecated(forRemoval = true)
    @Test
    void testFindEndpointAndPropertiesMigratableSecrets() {
        // Create a few camel and webhook endpoints that should be picked by
        // the function under test, since they don't have sources references.
        final Map<UUID, Endpoint> multipleFetchableEndpoints = this.resourceHelpers.createTenEndpointFixtures();
        final Map<UUID, Endpoint> fetchableEndpointsNullEmptybasicAuth = this.resourceHelpers.createFiveEndpointsNullEmptyBasicAuths();

        // Combine the two maps into one single map of expected fetchable
        // endpoints.
        final Map<UUID, Endpoint> expectedEndpointsToFetch = Stream
            .concat(
                multipleFetchableEndpoints.entrySet().stream(),
                fetchableEndpointsNullEmptybasicAuth.entrySet().stream())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // Create a few endpoints that shouldn't be picked by the function
        // under test, because they contain references to Sources' secrets.
        this.resourceHelpers.createTwelveEndpointFixtures();

        // Call the function under test.
        final Map<UUID, String> eligibleEndpoints = this.endpointRepository.findEndpointWithPropertiesWithStoredSecrets();

        // Assert that the only endpoints that were fetched were the expected ones.
        Assertions.assertEquals(expectedEndpointsToFetch.size(), eligibleEndpoints.size(), "unexpected number of eligible endpoints identified");

        // Assert that the endpoints and its properties are the same.
        for (final Map.Entry<UUID, String> fetchedEndpoint : eligibleEndpoints.entrySet()) {
            final Endpoint expectedEndpoint = expectedEndpointsToFetch.get(fetchedEndpoint.getKey());
            Assertions.assertNotNull(expectedEndpoint, "the identified endpoint from the database wasn't supposed to be fetched");
            Assertions.assertEquals(expectedEndpoint.getOrgId(), fetchedEndpoint.getValue(), "the org id of the identified endpoint doesn't match");
        }
    }

    /**
     * Creates a camel endpoint with camel properties, with the references to
     * the sources secrets empty.
     * @return the created endpoint.
     */
    @Deprecated(forRemoval = true)
    private Endpoint createCamelEndpointWithCamelProperties() {
        final CamelProperties camelProperties = new CamelProperties();
        camelProperties.setBasicAuthentication(new BasicAuthentication("a", "b"));
        camelProperties.setDisableSslVerification(true);
        camelProperties.setExtras(Map.of("a", "b"));
        camelProperties.setSecretToken("secret token!");
        camelProperties.setUrl("https://example.com");

        final Endpoint camelEndpoint = new Endpoint();
        camelEndpoint.setAccountId(DEFAULT_ACCOUNT_ID);
        camelEndpoint.setCreated(LocalDateTime.now(Clock.systemUTC()));
        camelEndpoint.setDescription("description");
        camelEndpoint.setEnabled(true);
        camelEndpoint.setName("name");
        camelEndpoint.setOrgId(DEFAULT_ORG_ID);
        camelEndpoint.setProperties(camelProperties);
        camelEndpoint.setSubType("splunk");
        camelEndpoint.setType(EndpointType.CAMEL);

        return this.endpointRepository.createEndpoint(camelEndpoint);
    }

    /**
     * Creates a webhook endpoint with webhook properties, with the references
     * to the sources secrets empty.
     * @return the created endpoint.
     */
    @Deprecated(forRemoval = true)
    private Endpoint createWebhookEndpointWithWebhookProperties() {
        final WebhookProperties webhookProperties = new WebhookProperties();
        webhookProperties.setBasicAuthentication(new BasicAuthentication("a", "b"));
        webhookProperties.setDisableSslVerification(true);
        webhookProperties.setMethod(HttpType.GET);
        webhookProperties.setSecretToken("secret token!");
        webhookProperties.setUrl("https://example.com");

        final Endpoint webhookEndpoint = new Endpoint();
        webhookEndpoint.setAccountId(DEFAULT_ACCOUNT_ID);
        webhookEndpoint.setCreated(LocalDateTime.now(Clock.systemUTC()));
        webhookEndpoint.setDescription("description");
        webhookEndpoint.setEnabled(true);
        webhookEndpoint.setName("name");
        webhookEndpoint.setOrgId(DEFAULT_ORG_ID);
        webhookEndpoint.setProperties(webhookProperties);
        webhookEndpoint.setType(EndpointType.WEBHOOK);

        return this.endpointRepository.createEndpoint(webhookEndpoint);
    }
}
