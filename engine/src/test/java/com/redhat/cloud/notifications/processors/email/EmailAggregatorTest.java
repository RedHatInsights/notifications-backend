package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.repositories.EmailAggregationRepository;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.SystemSubscriptionProperties;
import com.redhat.cloud.notifications.recipients.RecipientResolver;
import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.recipients.recipientsresolver.RecipientsResolverService;
import com.redhat.cloud.notifications.recipients.recipientsresolver.pojo.RecipientsQuery;
import io.quarkus.cache.CacheInvalidate;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.inject.Inject;
import org.apache.commons.lang3.RandomStringUtils;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.models.SubscriptionType.DAILY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@QuarkusTest
class EmailAggregatorTest {

    @InjectSpy
    EmailAggregationRepository emailAggregationRepository;

    @InjectMock
    RecipientResolver recipientResolver;

    @InjectMock
    @RestClient
    RecipientsResolverService recipientsResolverService;

    @Inject
    FeatureFlipper featureFlipper;

    @InjectSpy
    EmailAggregator emailAggregator;

    @Inject
    ResourceHelpers resourceHelpers;

    @InjectMock
    EndpointRepository endpointRepository;

    Application application;
    EventType eventType1;
    EventType eventType2;
    final EmailAggregationKey aggregationKey = new EmailAggregationKey("org-1", "rhel", "policies");

    @BeforeEach
    void beforeEach() {
        emailAggregator.maxPageSize = 5;
        clearCachedData();
        clearInvocations(recipientsResolverService);
        clearInvocations(recipientResolver);
        emailAggregationRepository.purgeOldAggregation(aggregationKey, LocalDateTime.now(ZoneOffset.UTC).plusMinutes(1));
    }

    @AfterEach
    void afterEach() {
        resourceHelpers.deleteEventTypeEmailSubscription("org-1", "user-2", eventType2, DAILY);
        resourceHelpers.deleteEventTypeEmailSubscription("org-1", "user-2", eventType1, DAILY);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldTestRecipientsFromSubscription(boolean useRecipientsResolverClowdappForDailyDigestEnabled) {

        featureFlipper.setUseRecipientsResolverClowdappForDailyDigestEnabled(useRecipientsResolverClowdappForDailyDigestEnabled);
        // init test environment
        application = resourceHelpers.findApp("rhel", "policies");
        eventType1 = resourceHelpers.findOrCreateEventType(application.getId(), TestHelpers.eventType);
        eventType2 = resourceHelpers.findOrCreateEventType(application.getId(), "not-used");
        resourceHelpers.findOrCreateEventType(application.getId(), "event-type-2");
        resourceHelpers.createEventTypeEmailSubscription("org-1", "user-2", eventType2, DAILY);

        Endpoint endpoint = new Endpoint();
        endpoint.setProperties(new SystemSubscriptionProperties());
        endpoint.setType(EndpointType.EMAIL_SUBSCRIPTION);

        when(endpointRepository.getTargetEmailSubscriptionEndpoints(anyString(), any(UUID.class))).thenReturn(List.of(endpoint));

        if (featureFlipper.isUseRecipientsResolverClowdappForDailyDigestEnabled()) {
            when(recipientsResolverService.getRecipients(any(RecipientsQuery.class))).then(parameters -> {
                RecipientsQuery query = parameters.getArgument(0);
                Set<String> users = query.subscribers;
                return users.stream().map(usrStr -> {
                    User usr = new User();
                    usr.setEmail(usrStr);
                    return usr;
                }).collect(Collectors.toSet());
            });
        } else {
            when(recipientResolver.recipientUsers(anyString(), any(), any())).then(parameters -> {
                Set<String> users = parameters.getArgument(2);
                return users.stream().map(usrStr -> {
                    User usr = new User();
                    usr.setEmail(usrStr);
                    return usr;
                }).collect(Collectors.toSet());
            });
        }

        // Test user subscription based on event type
        Map<User, Map<String, Object>> result = aggregate();
        verify(emailAggregationRepository, times(1)).getEmailAggregation(any(EmailAggregationKey.class), any(LocalDateTime.class), any(LocalDateTime.class), anyInt(), anyInt());
        verify(emailAggregationRepository, times(1)).getEmailAggregation(any(EmailAggregationKey.class), any(LocalDateTime.class), any(LocalDateTime.class), eq(0), eq(emailAggregator.maxPageSize));
        verifyRecipientsResolverInteractions(4);

        // nobody subscribed to the right event type yet
        assertEquals(0, result.size());
        clearInvocations(recipientsResolverService); // just reset mockito counter
        clearInvocations(emailAggregationRepository);
        resourceHelpers.createEventTypeEmailSubscription("org-1", "user-2", eventType1, DAILY);
        // because after the previous aggregate() call the email_aggregation DB table was not purged, we already have 4 records on database
        result = aggregate();
        verify(emailAggregationRepository, times(2)).getEmailAggregation(any(EmailAggregationKey.class), any(LocalDateTime.class), any(LocalDateTime.class), anyInt(), anyInt());
        verify(emailAggregationRepository, times(1)).getEmailAggregation(any(EmailAggregationKey.class), any(LocalDateTime.class), any(LocalDateTime.class), eq(0), eq(emailAggregator.maxPageSize));
        verify(emailAggregationRepository, times(1)).getEmailAggregation(any(EmailAggregationKey.class), any(LocalDateTime.class), any(LocalDateTime.class), eq(5), eq(emailAggregator.maxPageSize));
        assertEquals(1, result.size());
        User user = result.keySet().stream().findFirst().get();
        assertTrue(user.getEmail().equals("user-2"));
        assertEquals(8, ((LinkedHashMap) result.get(user).get("policies")).size());
        verifyRecipientsResolverInteractions(12);
    }

    @Test
    void shouldTestFallbackOnLegacyRecipientsResolverFetching() {

        featureFlipper.setUseRecipientsResolverClowdappForDailyDigestEnabled(true);
        // init test environment
        application = resourceHelpers.findApp("rhel", "policies");
        eventType1 = resourceHelpers.findOrCreateEventType(application.getId(), TestHelpers.eventType);
        eventType2 = resourceHelpers.findOrCreateEventType(application.getId(), "not-used");
        resourceHelpers.findOrCreateEventType(application.getId(), "event-type-2");
        resourceHelpers.createEventTypeEmailSubscription("org-1", "user-2", eventType2, DAILY);

        Endpoint endpoint = new Endpoint();
        endpoint.setProperties(new SystemSubscriptionProperties());
        endpoint.setType(EndpointType.EMAIL_SUBSCRIPTION);

        when(endpointRepository.getTargetEmailSubscriptionEndpoints(anyString(), any(UUID.class))).thenReturn(List.of(endpoint));

        when(recipientsResolverService.getRecipients(any())).thenThrow(RuntimeException.class);

        when(recipientResolver.recipientUsers(anyString(), any(), any())).then(parameters -> {
            Set<String> users = parameters.getArgument(2);
            return users.stream().map(usrStr -> {
                User usr = new User();
                usr.setEmail(usrStr);
                return usr;
            }).collect(Collectors.toSet());
        });

        // Test user subscription based on event type
        Map<User, Map<String, Object>> result = aggregate();
        verify(emailAggregationRepository, times(1)).getEmailAggregation(any(EmailAggregationKey.class), any(LocalDateTime.class), any(LocalDateTime.class), anyInt(), anyInt());
        verify(emailAggregationRepository, times(1)).getEmailAggregation(any(EmailAggregationKey.class), any(LocalDateTime.class), any(LocalDateTime.class), eq(0), eq(emailAggregator.maxPageSize));
        verify(recipientsResolverService, times(4)).getRecipients(any());
        verify(recipientResolver, times(4)).recipientUsers(anyString(), any(), any());

        // nobody subscribed to the right event type yet
        assertEquals(0, result.size());
    }

    private void verifyRecipientsResolverInteractions(int legacyRecipientResolverInvocations) {
        if (featureFlipper.isUseRecipientsResolverClowdappForDailyDigestEnabled()) {
            verify(recipientsResolverService, times(1)).getRecipients(any());
            verifyNoInteractions(recipientResolver);
        } else {
            verify(recipientResolver, times(legacyRecipientResolverInvocations)).recipientUsers(anyString(), any(), any());
            verifyNoInteractions(recipientsResolverService);
        }
    }

    private Map<User, Map<String, Object>> aggregate() {
        Map<User, Map<String, Object>> result = new HashMap<>();
        emailAggregationRepository.addEmailAggregation(TestHelpers.createEmailAggregation("org-1", "rhel", "policies", RandomStringUtils.random(10), RandomStringUtils.random(10)));
        emailAggregationRepository.addEmailAggregation(TestHelpers.createEmailAggregation("org-1", "rhel", "policies", RandomStringUtils.random(10), RandomStringUtils.random(10)));
        emailAggregationRepository.addEmailAggregation(TestHelpers.createEmailAggregation("org-1", "rhel", "policies", RandomStringUtils.random(10), RandomStringUtils.random(10)));
        emailAggregationRepository.addEmailAggregation(TestHelpers.createEmailAggregation("org-1", "rhel", "policies", RandomStringUtils.random(10), RandomStringUtils.random(10)));

        emailAggregationRepository.addEmailAggregation(TestHelpers.createEmailAggregation("org-2", "rhel", "policies", RandomStringUtils.random(10), RandomStringUtils.random(10)));
        result.putAll(emailAggregator.getAggregated(application.getId(), aggregationKey, DAILY, LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1), LocalDateTime.now(ZoneOffset.UTC).plusMinutes(1)));
        return result;
    }

    @CacheInvalidate(cacheName = "recipients-resolver-results")
    void clearCachedData() {
        /*
         * This would normally happen after a certain duration fixed in application.properties with the
         * quarkus.cache.caffeine.recipients-resolver-results.expire-after-write key.
         */
    }
}
