package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.MicrometerAssertionHelper;
import com.redhat.cloud.notifications.PatchTestHelpers;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.config.EngineConfig;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.repositories.EmailAggregationRepository;
import com.redhat.cloud.notifications.db.repositories.TemplateRepository;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Parser;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.models.AggregationCommand;
import com.redhat.cloud.notifications.models.AggregationEmailTemplate;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventAggregationCriteria;
import com.redhat.cloud.notifications.processors.ConnectorSender;
import com.redhat.cloud.notifications.processors.email.connector.dto.EmailNotification;
import com.redhat.cloud.notifications.recipients.RecipientSettings;
import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.recipients.recipientsresolver.ExternalRecipientsResolver;
import com.redhat.cloud.notifications.templates.EmailTemplateMigrationService;
import com.redhat.cloud.notifications.transformers.BaseTransformer;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.events.EventConsumer.INGRESS_CHANNEL;
import static com.redhat.cloud.notifications.models.SubscriptionType.DAILY;
import static com.redhat.cloud.notifications.processors.email.EmailAggregationProcessor.AGGREGATION_COMMAND_ERROR_COUNTER_NAME;
import static com.redhat.cloud.notifications.processors.email.EmailAggregationProcessor.AGGREGATION_COMMAND_PROCESSED_COUNTER_NAME;
import static com.redhat.cloud.notifications.processors.email.EmailAggregationProcessor.AGGREGATION_COMMAND_REJECTED_COUNTER_NAME;
import static com.redhat.cloud.notifications.processors.email.EmailAggregationProcessor.AGGREGATION_CONSUMED_TIMER_NAME;
import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@QuarkusTest
class EmailAggregationProcessorTest {

    final String BUNDLE_NAME = "console";
    final String APP_NAME = "notifications";
    final String EVENT_TYPE_NAME = "aggregation";

    @Inject
    @Any
    InMemoryConnector inMemoryConnector;

    @InjectSpy
    EmailAggregationRepository emailAggregationRepository;

    @InjectMock
    ExternalRecipientsResolver externalRecipientsResolver;

    @InjectMock
    ConnectorSender connectorSender;

    @Inject
    MicrometerAssertionHelper micrometerAssertionHelper;

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    EmailTemplateMigrationService emailTemplateMigrationService;

    @InjectSpy
    TemplateRepository templateRepository;

    @InjectSpy
    EngineConfig engineConfig;

    static User user1 = new User();
    static User user2 = new User();
    static User user3 = new User();

    String DEFAULT_ORG_ID;

    @BeforeAll
    static void initTest() {
        user1.setUsername("foo");
        user1.setEmail("foo@redhat.com");
        user2.setUsername("bar");
        user2.setEmail("bar@redhat.com");
        user3.setUsername("user3");
        user3.setEmail("user3@redhat.com");
    }

    @BeforeEach
    void beforeEach() {
        micrometerAssertionHelper.removeDynamicTimer(AGGREGATION_CONSUMED_TIMER_NAME);
        DEFAULT_ORG_ID = RandomStringUtils.secure().nextAlphanumeric(6);
        micrometerAssertionHelper.saveCounterValuesBeforeTest(AGGREGATION_COMMAND_REJECTED_COUNTER_NAME, AGGREGATION_COMMAND_PROCESSED_COUNTER_NAME, AGGREGATION_COMMAND_ERROR_COUNTER_NAME);
        createAggregatorEventTypeIfNeeded();
        mockUsers(user1, user2, user3);
        when(templateRepository.findAggregationEmailTemplate(anyString(), anyString(), eq(DAILY))).thenCallRealMethod();
        resourceHelpers.clearEvents();
    }

    @AfterEach
    void afterEach() {
        resourceHelpers.deleteApp("rhel", "patch");
        resourceHelpers.clearEvents();
    }

    private EmailAggregationKey buildEmailAggregationKey(String orgId, String bundleName, String appName) {
        if (engineConfig.isAggregationBasedOnEventEnabled(orgId)) {
            Application app = resourceHelpers.findApp(bundleName, appName);
            return new EventAggregationCriteria(orgId, app.getBundleId(), app.getId(), bundleName, appName);
        }
        return new EmailAggregationKey(orgId, bundleName, appName);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldSuccessfullySendOneAggregatedEmailWithTwoRecipients(final boolean useAggregationBasedOnEventTable) {

        when(engineConfig.isAggregationBasedOnEventEnabled(anyString())).thenReturn(useAggregationBasedOnEventTable);
        resourceHelpers.createBlankAggregationEmailTemplate("bundle-2", "app-2");

        final String ORG_ID_1 = RandomStringUtils.secure().nextAlphanumeric(6);
        final String ORG_ID_2 = RandomStringUtils.secure().nextAlphanumeric(6);

        // Because this test will use a real Payload Aggregator
        EmailAggregationKey aggregationKey1 = buildEmailAggregationKey(ORG_ID_1, "rhel", "policies");
        EmailAggregationKey aggregationKey2 = buildEmailAggregationKey(ORG_ID_2, "bundle-2", "app-2");

        List<EmailAggregation> eventToAggregate = List.of(
            TestHelpers.createEmailAggregation(ORG_ID_1, "rhel", "policies", RandomStringUtils.random(10), RandomStringUtils.random(10)),
            TestHelpers.createEmailAggregation(ORG_ID_1, "rhel", "policies", RandomStringUtils.random(10), RandomStringUtils.random(10), "user3")
        );

        createAggregationsAndSendAggregationKeysToIngress(useAggregationBasedOnEventTable, eventToAggregate, aggregationKey1, aggregationKey2);

        ArgumentCaptor<JsonObject> argumentCaptor = ArgumentCaptor.forClass(JsonObject.class);
        verify(connectorSender, timeout(5000L).times(2)).send(any(Event.class), any(Endpoint.class), argumentCaptor.capture());
        List<JsonObject> capturedPayloads = argumentCaptor.getAllValues();
        assertTrue(
            capturedPayloads.stream().anyMatch(capturedPayload -> {
                EmailNotification capturedEmailRequest = capturedPayload.mapTo(EmailNotification.class);
                Collection<String> subscribers = capturedEmailRequest.subscribers();
                return subscribers.size() == 2 && subscribers.contains(user1.getUsername()) && subscribers.contains(user2.getUsername());
            })
        );
        assertTrue(
            capturedPayloads.stream().anyMatch(capturedPayload -> {
                EmailNotification capturedEmailRequest = capturedPayload.mapTo(EmailNotification.class);
                Collection<String> subscribers = capturedEmailRequest.subscribers();
                return subscribers.size() == 1 && subscribers.contains(user3.getUsername());
            })
        );

        capturedPayloads.stream().forEach(capturedPayload -> {
            EmailNotification capturedEmailRequest = capturedPayload.mapTo(EmailNotification.class);
            assertEquals("Daily digest - Red Hat Enterprise Linux", capturedEmailRequest.emailSubject());
            assertTrue(capturedEmailRequest.emailBody().contains("Daily digest - Red Hat Enterprise Linux"));
            assertTrue(capturedEmailRequest.emailBody().contains("Jump to details"));
        });

        resourceHelpers.deleteBundle("bundle-2");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldSuccessfullySendOneAggregatedEmailWithTwoRecipientsWithTwoApps(final boolean useAggregationBasedOnEventTable) {
        try {
            when(engineConfig.isAggregationBasedOnEventEnabled(anyString())).thenReturn(useAggregationBasedOnEventTable);
            initData("patch", "new-advisory");

            // Because this test will use a real Payload Aggregator
            EmailAggregationKey aggregationKey1 = buildEmailAggregationKey(DEFAULT_ORG_ID, "rhel", "policies");
            EmailAggregationKey aggregationKey2 = buildEmailAggregationKey(DEFAULT_ORG_ID, "rhel", "patch");

            List<EmailAggregation> eventToAggregate = List.of(
                TestHelpers.createEmailAggregation(DEFAULT_ORG_ID, "rhel", "policies", RandomStringUtils.random(10), RandomStringUtils.random(10)),
                TestHelpers.createEmailAggregation(DEFAULT_ORG_ID, "rhel", "policies", RandomStringUtils.random(10), RandomStringUtils.random(10), "user3"),
                PatchTestHelpers.createEmailAggregation(DEFAULT_ORG_ID, "rhel", "patch", "advisory_1", "test synopsis", "security", "host-01"),
                PatchTestHelpers.createEmailAggregation(DEFAULT_ORG_ID, "rhel", "patch", "advisory_2", "test synopsis", "enhancement", "host-01"),
                PatchTestHelpers.createEmailAggregation(DEFAULT_ORG_ID, "rhel", "patch", "advisory_3", "test synopsis", "enhancement", "host-02")
            );

            createAggregationsAndSendAggregationKeysToIngress(useAggregationBasedOnEventTable, eventToAggregate, aggregationKey1, aggregationKey2);

            ArgumentCaptor<JsonObject> argumentCaptor = ArgumentCaptor.forClass(JsonObject.class);
            verify(connectorSender, timeout(5000L).times(2)).send(any(Event.class), any(Endpoint.class), argumentCaptor.capture());
            List<JsonObject> capturedPayloads = argumentCaptor.getAllValues();
            assertTrue(
                capturedPayloads.stream().anyMatch(capturedPayload -> {
                    EmailNotification capturedEmailRequest = capturedPayload.mapTo(EmailNotification.class);
                    Collection<String> subscribers = capturedEmailRequest.subscribers();
                    return subscribers.size() == 2 && subscribers.contains(user1.getUsername()) && subscribers.contains(user2.getUsername());
                })
            );
            assertTrue(
                capturedPayloads.stream().anyMatch(capturedPayload -> {
                    EmailNotification capturedEmailRequest = capturedPayload.mapTo(EmailNotification.class);
                    Collection<String> subscribers = capturedEmailRequest.subscribers();
                    return subscribers.size() == 1 && subscribers.contains(user3.getUsername());
                })
            );

            capturedPayloads.stream().forEach(capturedPayload -> {
                EmailNotification capturedEmailRequest = capturedPayload.mapTo(EmailNotification.class);
                assertEquals("Daily digest - Red Hat Enterprise Linux", capturedEmailRequest.emailSubject());
                assertTrue(capturedEmailRequest.emailBody().contains("Daily digest - Red Hat Enterprise Linux"));
                assertTrue(capturedEmailRequest.emailBody().contains("Jump to details"));
                assertTrue(capturedEmailRequest.emailBody().contains("id=\"policies-section1\""));
            });

            long nbEmailWithPoliciesAndPatchApps = capturedPayloads.stream().filter(capturedPayload -> {
                EmailNotification capturedEmailRequest = capturedPayload.mapTo(EmailNotification.class);
                return capturedEmailRequest.emailBody().contains("id=\"policies-section1\"")
                    && capturedEmailRequest.emailBody().contains("id=\"patch-section1\"");
            }).count();
            assertEquals(1, nbEmailWithPoliciesAndPatchApps);

            micrometerAssertionHelper.clearSavedValues();
        } finally {
            resourceHelpers.deleteApp("rhel", "patch");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldSuccessfullySendOneAggEmailWithOneAppHandlingAggregationError(final boolean useAggregationBasedOnEventTable) {
        try {
            when(engineConfig.isAggregationBasedOnEventEnabled(anyString())).thenReturn(useAggregationBasedOnEventTable);
            initData("patch", "new-advisory");

            // Because this test will use a real Payload Aggregator
            EmailAggregationKey aggregationKey1 = buildEmailAggregationKey(DEFAULT_ORG_ID, "rhel", "policies");
            EmailAggregationKey aggregationKey2 = buildEmailAggregationKey(DEFAULT_ORG_ID, "rhel", "patch");

            List<EmailAggregation> eventToAggregate = new ArrayList<>();
            eventToAggregate.add(TestHelpers.createEmailAggregation(DEFAULT_ORG_ID, "rhel", "policies", RandomStringUtils.random(10), RandomStringUtils.random(10)));
            eventToAggregate.add(TestHelpers.createEmailAggregation(DEFAULT_ORG_ID, "rhel", "policies", RandomStringUtils.random(10), RandomStringUtils.random(10), "user3"));
            eventToAggregate.add(PatchTestHelpers.createEmailAggregation(DEFAULT_ORG_ID, "rhel", "patch", "advisory_1", "test synopsis", "security", "host-01"));
            eventToAggregate.add(PatchTestHelpers.createEmailAggregation(DEFAULT_ORG_ID, "rhel", "patch", "advisory_2", "test synopsis", "enhancement", "host-01"));
            eventToAggregate.add(PatchTestHelpers.createEmailAggregation(DEFAULT_ORG_ID, "rhel", "patch", "advisory_3", "test synopsis", "enhancement", "host-02"));

            EmailAggregation errorOnPoliciesPayload = TestHelpers.createEmailAggregation(DEFAULT_ORG_ID, "rhel", "policies", RandomStringUtils.random(10), RandomStringUtils.random(10));
            errorOnPoliciesPayload.getPayload().getMap().put("events", "Wrong format");
            eventToAggregate.add(errorOnPoliciesPayload);

            createAggregationsAndSendAggregationKeysToIngress(useAggregationBasedOnEventTable, eventToAggregate, aggregationKey1, aggregationKey2);

            ArgumentCaptor<JsonObject> argumentCaptor = ArgumentCaptor.forClass(JsonObject.class);
            verify(connectorSender, timeout(5000L).times(1)).send(any(Event.class), any(Endpoint.class), argumentCaptor.capture());
            List<JsonObject> capturedPayloads = argumentCaptor.getAllValues();
            assertTrue(
                capturedPayloads.stream().anyMatch(capturedPayload -> {
                    EmailNotification capturedEmailRequest = capturedPayload.mapTo(EmailNotification.class);
                    Collection<String> subscribers = capturedEmailRequest.subscribers();
                    return subscribers.size() == 2 && subscribers.contains(user1.getUsername()) && subscribers.contains(user2.getUsername());
                })
            );

            long nbEmailWithPoliciesAndPatchApps = capturedPayloads.stream().filter(capturedPayload -> {
                EmailNotification capturedEmailRequest = capturedPayload.mapTo(EmailNotification.class);
                return !capturedEmailRequest.emailBody().contains("id=\"policies-section1\"")
                    && capturedEmailRequest.emailBody().contains("id=\"patch-section1\"");
            }).count();
            assertEquals(1, nbEmailWithPoliciesAndPatchApps);

            micrometerAssertionHelper.clearSavedValues();
        } finally {
            resourceHelpers.deleteApp("rhel", "patch");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldSuccessfullySendOneAggEmailWithOneAppHandlingTemplateError(final boolean useAggregationBasedOnEventTable) {
        try {
            when(engineConfig.isAggregationBasedOnEventEnabled(anyString())).thenReturn(useAggregationBasedOnEventTable);
            when(templateRepository.findAggregationEmailTemplate(anyString(), anyString(), eq(DAILY))).thenCallRealMethod();

            when(templateRepository.findAggregationEmailTemplate(anyString(), anyString(), eq(DAILY)))
                .thenAnswer(new Answer<Object>() {
                    @Override
                    public Object answer(InvocationOnMock invocation) throws Throwable {
                        Object[] args = invocation.getArguments();
                        // Process or modify the arguments as needed
                        String application = (String) args[1];

                        // Call the real method with the modified arguments
                        Optional<AggregationEmailTemplate> optTemplate = (Optional<AggregationEmailTemplate>) invocation.callRealMethod();
                        if ("patch".equals(application)) {
                            optTemplate.get().getBodyTemplate().setData("{errorTemplate}");
                        }

                        // Perform any additional processing if necessary
                        return optTemplate;
                    }
                });

            initData("patch", "new-advisory");

            // Because this test will use a real Payload Aggregator
            EmailAggregationKey aggregationKey1 = buildEmailAggregationKey(DEFAULT_ORG_ID, "rhel", "policies");
            EmailAggregationKey aggregationKey2 = buildEmailAggregationKey(DEFAULT_ORG_ID, "rhel", "patch");

            List<EmailAggregation> eventToAggregate = List.of(
                TestHelpers.createEmailAggregation(DEFAULT_ORG_ID, "rhel", "policies", RandomStringUtils.random(10), RandomStringUtils.random(10)),
                TestHelpers.createEmailAggregation(DEFAULT_ORG_ID, "rhel", "policies", RandomStringUtils.random(10), RandomStringUtils.random(10), "user3"),
                PatchTestHelpers.createEmailAggregation(DEFAULT_ORG_ID, "rhel", "patch", "advisory_1", "test synopsis", "security", "host-01"),
                PatchTestHelpers.createEmailAggregation(DEFAULT_ORG_ID, "rhel", "patch", "advisory_2", "test synopsis", "enhancement", "host-01"),
                PatchTestHelpers.createEmailAggregation(DEFAULT_ORG_ID, "rhel", "patch", "advisory_3", "test synopsis", "enhancement", "host-02")
            );

            createAggregationsAndSendAggregationKeysToIngress(useAggregationBasedOnEventTable, eventToAggregate, aggregationKey1, aggregationKey2);

            ArgumentCaptor<JsonObject> argumentCaptor = ArgumentCaptor.forClass(JsonObject.class);
            verify(connectorSender, timeout(5000L).times(2)).send(any(Event.class), any(Endpoint.class), argumentCaptor.capture());
            List<JsonObject> capturedPayloads = argumentCaptor.getAllValues();
            assertTrue(
                capturedPayloads.stream().anyMatch(capturedPayload -> {
                    EmailNotification capturedEmailRequest = capturedPayload.mapTo(EmailNotification.class);
                    Collection<String> subscribers = capturedEmailRequest.subscribers();
                    return subscribers.size() == 2 && subscribers.contains(user1.getUsername()) && subscribers.contains(user2.getUsername());
                })
            );
            assertTrue(
                capturedPayloads.stream().anyMatch(capturedPayload -> {
                    EmailNotification capturedEmailRequest = capturedPayload.mapTo(EmailNotification.class);
                    Collection<String> subscribers = capturedEmailRequest.subscribers();
                    return subscribers.size() == 1 && subscribers.contains(user3.getUsername());
                })
            );

            capturedPayloads.stream().forEach(capturedPayload -> {
                EmailNotification capturedEmailRequest = capturedPayload.mapTo(EmailNotification.class);
                assertEquals("Daily digest - Red Hat Enterprise Linux", capturedEmailRequest.emailSubject());
                assertTrue(capturedEmailRequest.emailBody().contains("Daily digest - Red Hat Enterprise Linux"));
                assertTrue(capturedEmailRequest.emailBody().contains("Jump to details"));
                assertTrue(capturedEmailRequest.emailBody().contains("id=\"policies-section1\""));
                assertFalse(capturedEmailRequest.emailBody().contains("id=\"patch-section1\""));
            });

            micrometerAssertionHelper.clearSavedValues();
        } finally {
            resourceHelpers.deleteApp("rhel", "patch");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldNotSendAggEmailBecauseNoAppSucceedToRender(final boolean useAggregationBasedOnEventTable) {
        when(engineConfig.isAggregationBasedOnEventEnabled(anyString())).thenReturn(useAggregationBasedOnEventTable);
        when(templateRepository.findAggregationEmailTemplate(anyString(), anyString(), eq(DAILY))).thenCallRealMethod();

        // Because this test will use a real Payload Aggregator
        EmailAggregationKey aggregationKey1 = buildEmailAggregationKey(DEFAULT_ORG_ID, "rhel", "policies");

        List<EmailAggregation> eventToAggregate = new ArrayList<>();
        eventToAggregate.add(TestHelpers.createEmailAggregation(DEFAULT_ORG_ID, "rhel", "policies", RandomStringUtils.random(10), RandomStringUtils.random(10)));

        EmailAggregation errorOnPoliciesPayload = TestHelpers.createEmailAggregation(DEFAULT_ORG_ID, "rhel", "policies", RandomStringUtils.random(10), RandomStringUtils.random(10));
        errorOnPoliciesPayload.getPayload().getMap().put("events", "Wrong format");
        eventToAggregate.add(errorOnPoliciesPayload);

        createAggregationsAndSendAggregationKeysToIngress(useAggregationBasedOnEventTable, eventToAggregate, aggregationKey1);

        verifyNoInteractions(connectorSender);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldNotSendAggregatedEmailBecausePatchAggIsEmpty(final boolean useAggregationBasedOnEventTable) {
        when(engineConfig.isAggregationBasedOnEventEnabled(anyString())).thenReturn(useAggregationBasedOnEventTable);

        try {
            initData("patch", "new-advisory");

            // Because this test will use a real Payload Aggregator
            EmailAggregationKey aggregationKey1 = buildEmailAggregationKey(DEFAULT_ORG_ID, "rhel", "patch");

            List<EmailAggregation> eventToAggregate = List.of(
                PatchTestHelpers.createEmailAggregation(DEFAULT_ORG_ID, "rhel", "patch", "advisory_1", "test synopsis", "unknown", "host-01"),
                PatchTestHelpers.createEmailAggregation(DEFAULT_ORG_ID, "rhel", "patch", "advisory_2", "test synopsis", "unknown", "host-01")
            );

            createAggregationsAndSendAggregationKeysToIngress(useAggregationBasedOnEventTable, eventToAggregate, aggregationKey1);

            verifyNoInteractions(connectorSender);

        } finally {
            resourceHelpers.deleteApp("rhel", "patch");
        }
    }

    private void mockUsers(User user1, User user2, User user3) {
        when(externalRecipientsResolver.recipientUsers(anyString(), anySet(), anySet(), anySet(), anyBoolean(), any()))
            .then(invocation -> {
                    Set<RecipientSettings> list = invocation.getArgument(1);
                    if (list.isEmpty()) {
                        return Set.of(user1, user2);
                    }
                    return Set.of(user1, user2, user3);
                }
            );
    }

    private void createAggregatorEventTypeIfNeeded() {
        Application aggregatorApp = resourceHelpers.findOrCreateApplication(BUNDLE_NAME, APP_NAME);
        String eventTypeName = EVENT_TYPE_NAME;
        try {
            resourceHelpers.findEventType(aggregatorApp.getId(), eventTypeName);
        } catch (NoResultException nre) {
            resourceHelpers.createEventType(aggregatorApp.getId(), eventTypeName);
        }
    }

    private String buildAggregatorActionFromKey(List<EmailAggregationKey> aggregationKeys) {
        List<AggregationCommand> aggregationCommands = new ArrayList<>();
        for (EmailAggregationKey aggregationKey : aggregationKeys) {
            AggregationCommand aggregationCommand = new AggregationCommand(
                aggregationKey,
                LocalDateTime.now(ZoneOffset.UTC).minusDays(1),
                LocalDateTime.now(ZoneOffset.UTC).plusDays(1),
                DAILY
            );
            aggregationCommands.add(aggregationCommand);
        }
        return buildAggregatorAction(aggregationCommands);
    }

    private String buildAggregatorAction(List<AggregationCommand> aggregationCommands) {

        String orgId = aggregationCommands.get(0).getOrgId();

        List<com.redhat.cloud.notifications.ingress.Event> events = new ArrayList<>();

        for (AggregationCommand aggregationCommand : aggregationCommands) {

            Payload.PayloadBuilder payloadBuilder = new Payload.PayloadBuilder();
            Map<String, Object> payload = JsonObject.mapFrom(aggregationCommand).getMap();
            payload.forEach(payloadBuilder::withAdditionalProperty);

            events.add(new com.redhat.cloud.notifications.ingress.Event.EventBuilder()
                        .withMetadata(new Metadata.MetadataBuilder().build())
                        .withPayload(payloadBuilder.build())
                        .build());
        }

        Action.ActionBuilder actionBuilder = (Action.ActionBuilder) new Action.ActionBuilder()
            .withBundle(BUNDLE_NAME)
            .withApplication(APP_NAME)
            .withEventType(EVENT_TYPE_NAME)
            .withOrgId(orgId)
            .withTimestamp(LocalDateTime.now(UTC))
            .withEvents(events);

        return Parser.encode(actionBuilder.build());
    }

    protected void initData(String app, String eventTypeToCreate) {

        Application application = resourceHelpers.findOrCreateApplication("rhel", app);
        resourceHelpers.findOrCreateEventType(application.getId(), eventTypeToCreate);

        emailTemplateMigrationService.deleteAllTemplates();
        emailTemplateMigrationService.migrate();
    }

    private void validateCommonAssertions(final boolean useAggregationBasedOnEventTable, EmailAggregationKey... aggregationKeys) {
        micrometerAssertionHelper.awaitAndAssertTimerIncrement(AGGREGATION_CONSUMED_TIMER_NAME, 1);
        micrometerAssertionHelper.awaitAndAssertCounterIncrement(AGGREGATION_COMMAND_PROCESSED_COUNTER_NAME, aggregationKeys.length);
        micrometerAssertionHelper.assertCounterIncrement(AGGREGATION_COMMAND_REJECTED_COUNTER_NAME, 0);
        micrometerAssertionHelper.assertCounterIncrement(AGGREGATION_COMMAND_ERROR_COUNTER_NAME, 0);

        for (EmailAggregationKey aggregationKey : aggregationKeys) {
            // Let's check that EndpointEmailSubscriptionResources#sendEmail was called for each aggregation.
            verify(emailAggregationRepository, timeout(5000L).times(useAggregationBasedOnEventTable ? 0 : 1)).getEmailAggregation(
                eq(aggregationKey),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                eq(0),
                anyInt()
            );
            verify(emailAggregationRepository, timeout(5000L).times(useAggregationBasedOnEventTable ? 1 : 0)).getEmailAggregationBasedOnEvent(
                useAggregationBasedOnEventTable ? eq((EventAggregationCriteria) aggregationKey) : any(EventAggregationCriteria.class),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                eq(0),
                anyInt()
            );

            verify(emailAggregationRepository, timeout(5000L).times(1)).purgeOldAggregation(
                eq(aggregationKey),
                any(LocalDateTime.class)
            );
        }
    }

    private void createAggregationsAndSendAggregationKeysToIngress(boolean useAggregationBasedOnEventTable, List<EmailAggregation> eventToAggregate, EmailAggregationKey... aggregationKeys) {
        for (EmailAggregation aggregation : eventToAggregate) {
            emailAggregationRepository.addEmailAggregation(aggregation);
            // the base transformer adds a "source" element which should not be present in an original event payload
            aggregation.getPayload().remove(BaseTransformer.SOURCE);
            resourceHelpers.addEventEmailAggregation(aggregation.getOrgId(), aggregation.getBundleName(), aggregation.getApplicationName(),  aggregation.getPayload());
        }

        inMemoryConnector.source(INGRESS_CHANNEL).send(buildAggregatorActionFromKey(Arrays.asList(aggregationKeys)));

        validateCommonAssertions(useAggregationBasedOnEventTable, aggregationKeys);
    }
}
