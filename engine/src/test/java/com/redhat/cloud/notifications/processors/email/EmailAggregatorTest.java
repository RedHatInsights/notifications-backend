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
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.inject.Inject;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.models.EmailSubscriptionType.DAILY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class EmailAggregatorTest {

    @InjectSpy
    EmailAggregationRepository emailAggregationRepository;

    @InjectMock
    RecipientResolver recipientResolver;

    @Inject
    FeatureFlipper featureFlipper;

    @InjectSpy
    EmailAggregator emailAggregator;

    @Inject
    ResourceHelpers resourceHelpers;

    @InjectMock
    EndpointRepository endpointRepository;

    final EmailAggregationKey aggregationKey = new EmailAggregationKey("org-1", "rhel", "policies");

    @BeforeEach
    void beforeEach() {
        emailAggregator.batchSize = 5;
    }

    @Test
    void shouldTestRecipientsFromSubscription() {

        // init test environment
        Application application = resourceHelpers.findApp("rhel", "policies");
        EventType eventType1 = resourceHelpers.createEventType(application.getId(), TestHelpers.eventType);
        EventType eventType2 = resourceHelpers.createEventType(application.getId(), "not-used");
        resourceHelpers.createEventType(application.getId(), "event-type-2");
        resourceHelpers.createEventTypeEmailSubscription("org-1", "user-2", eventType2, DAILY);

        Endpoint endpoint = new Endpoint();
        endpoint.setProperties(new SystemSubscriptionProperties());
        endpoint.setType(EndpointType.EMAIL_SUBSCRIPTION);

        when(endpointRepository.getTargetEmailSubscriptionEndpoints(anyString(), anyString(), anyString(), anyString())).thenReturn(List.of(endpoint));

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
        verify(emailAggregationRepository, times(1)).getEmailAggregation(any(EmailAggregationKey.class), any(LocalDateTime.class), any(LocalDateTime.class), eq(0), eq(emailAggregator.batchSize));
        reset(emailAggregationRepository); // just reset mockito counter

        // nobody subscribed to the right event type yet
        assertEquals(0, result.size());

        resourceHelpers.createEventTypeEmailSubscription("org-1", "user-2", eventType1, DAILY);
        // because after the previous aggregate() call the email_aggregation DB table was not purged, we already have 4 records on database
        result = aggregate();
        verify(emailAggregationRepository, times(2)).getEmailAggregation(any(EmailAggregationKey.class), any(LocalDateTime.class), any(LocalDateTime.class), anyInt(), anyInt());
        verify(emailAggregationRepository, times(1)).getEmailAggregation(any(EmailAggregationKey.class), any(LocalDateTime.class), any(LocalDateTime.class), eq(0), eq(emailAggregator.batchSize));
        verify(emailAggregationRepository, times(1)).getEmailAggregation(any(EmailAggregationKey.class), any(LocalDateTime.class), any(LocalDateTime.class), eq(5), eq(emailAggregator.batchSize));
        assertEquals(1, result.size());
        User user = result.keySet().stream().findFirst().get();
        assertTrue(user.getEmail().equals("user-2"));
        assertEquals(8, ((LinkedHashMap) result.get(user).get("policies")).size());

    }

    private Map<User, Map<String, Object>> aggregate() {
        Map<User, Map<String, Object>> result = new HashMap<>();
        emailAggregationRepository.addEmailAggregation(TestHelpers.createEmailAggregation("org-1", "rhel", "policies", RandomStringUtils.random(10), RandomStringUtils.random(10)));
        emailAggregationRepository.addEmailAggregation(TestHelpers.createEmailAggregation("org-1", "rhel", "policies", RandomStringUtils.random(10), RandomStringUtils.random(10)));
        emailAggregationRepository.addEmailAggregation(TestHelpers.createEmailAggregation("org-1", "rhel", "policies", RandomStringUtils.random(10), RandomStringUtils.random(10)));
        emailAggregationRepository.addEmailAggregation(TestHelpers.createEmailAggregation("org-1", "rhel", "policies", RandomStringUtils.random(10), RandomStringUtils.random(10)));

        emailAggregationRepository.addEmailAggregation(TestHelpers.createEmailAggregation("org-2", "rhel", "policies", RandomStringUtils.random(10), RandomStringUtils.random(10)));

        result.putAll(emailAggregator.getAggregated(aggregationKey, DAILY, LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1), LocalDateTime.now(ZoneOffset.UTC).plusMinutes(1)));
        return result;
    }
}
