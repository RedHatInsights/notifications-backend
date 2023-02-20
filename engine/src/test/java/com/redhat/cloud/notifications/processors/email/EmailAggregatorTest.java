package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.StatelessSessionFactory;
import com.redhat.cloud.notifications.db.repositories.EmailAggregationRepository;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import com.redhat.cloud.notifications.models.EmailSubscriptionProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.recipients.RecipientResolver;
import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.templates.Blank;
import com.redhat.cloud.notifications.templates.EmailTemplateFactory;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import javax.inject.Inject;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.models.EmailSubscriptionType.DAILY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@QuarkusTest
class EmailAggregatorTest {

    @InjectMock
    EmailTemplateFactory emailTemplateFactory;

    @Inject
    EmailAggregationRepository emailAggregationRepository;

    @InjectMock
    RecipientResolver recipientResolver;

    @Inject
    StatelessSessionFactory statelessSessionFactory;

    @Inject
    FeatureFlipper featureFlipper;

    @Inject
    EmailAggregator emailAggregator;

    @Inject
    ResourceHelpers resourceHelpers;

    @InjectMock
    EndpointRepository endpointRepository;

    @AfterEach
    void afterEach() {
        featureFlipper.setUseEventTypeForSubscriptionEnabled(false);
    }

    @Test
    void shouldTestRecipientsFromSubscription() {
        // init test environment
        Application application = resourceHelpers.findApp("rhel", "policies");
        EventType eventType1 = resourceHelpers.createEventType(application.getId(), TestHelpers.eventType);
        EventType eventType2 = resourceHelpers.createEventType(application.getId(), "not-used");
        resourceHelpers.createEventType(application.getId(), "event-type-2");
        resourceHelpers.createEmailSubscription("org-1", "user-1", application, DAILY);
        resourceHelpers.createEventTypeEmailSubscription("org-1", "user-2", eventType2, DAILY);

        Endpoint endpoint = new Endpoint();
        endpoint.setProperties(new EmailSubscriptionProperties());
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

        // Test user subscription based on application
        Map<User, Map<String, Object>> result = aggregate();
        assertNotNull(result);
        assertTrue(result.size() == 1);
        assertTrue(result.keySet().stream().filter(usr -> usr.getEmail().equals("user-1")).count() == 1);

        // Test user subscription based on event type
        featureFlipper.setUseEventTypeForSubscriptionEnabled(true);
        result = aggregate();

        // nobody subscribed to the right event type yet
        assertTrue(result.size() == 0);

        resourceHelpers.createEventTypeEmailSubscription("org-1", "user-2", eventType1, DAILY);
        result = aggregate();
        assertTrue(result.size() == 1);
        assertTrue(result.keySet().stream().filter(usr -> usr.getEmail().equals("user-2")).count() == 1);
    }

    private Map<User, Map<String, Object>> aggregate() {
        Map<User, Map<String, Object>> result = new HashMap<>();
        statelessSessionFactory.withSession(statelessSession -> {
            emailAggregationRepository.addEmailAggregation(TestHelpers.createEmailAggregation("org-1", "rhel", "policies", RandomStringUtils.random(10), RandomStringUtils.random(10)));
            emailAggregationRepository.addEmailAggregation(TestHelpers.createEmailAggregation("org-1", "rhel", "policies", RandomStringUtils.random(10), RandomStringUtils.random(10)));
            emailAggregationRepository.addEmailAggregation(TestHelpers.createEmailAggregation("org-2", "rhel", "policies", RandomStringUtils.random(10), RandomStringUtils.random(10)));

            EmailAggregationKey aggregationKey = new EmailAggregationKey("org-1", "rhel", "policies");

            when(emailTemplateFactory.get(anyString(), anyString())).thenReturn(new Blank());

            result.putAll(emailAggregator.getAggregated(aggregationKey, DAILY, LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1), LocalDateTime.now(ZoneOffset.UTC).plusMinutes(1)));
        });
        return result;
    }
}
