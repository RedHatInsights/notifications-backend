package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.config.EngineConfig;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.db.repositories.SubscriptionRepository;
import com.redhat.cloud.notifications.db.repositories.TemplateRepository;
import com.redhat.cloud.notifications.events.EventWrapper;
import com.redhat.cloud.notifications.events.EventWrapperAction;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Recipient;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.EventTypeKeyBundleAppEventTriplet;
import com.redhat.cloud.notifications.models.InstantEmailTemplate;
import com.redhat.cloud.notifications.models.SubscriptionType;
import com.redhat.cloud.notifications.models.SystemSubscriptionProperties;
import com.redhat.cloud.notifications.processors.ConnectorSender;
import com.redhat.cloud.notifications.processors.email.connector.dto.RecipientSettings;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.templates.TemplateService;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.redhat.cloud.notifications.TestHelpers.createPoliciesAction;
import static java.util.stream.Collectors.toSet;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

@QuarkusTest
public class EmailProcessorTest {
    @InjectMock
    ConnectorSender connectorSender;

    @InjectMock
    EmailActorsResolver emailActorsResolver;

    @Inject
    EmailProcessor emailProcessor;

    @InjectMock
    SubscriptionRepository subscriptionRepository;

    @InjectMock
    EndpointRepository endpointRepository;

    @InjectSpy
    TemplateRepository templateRepository;

    @InjectSpy
    TemplateService templateService;

    @InjectSpy
    com.redhat.cloud.notifications.qute.templates.TemplateService quteTemplateService;

    @InjectSpy
    EngineConfig engineConfig;

    /**
     * Creates a stub event with just the necessary elements to satisfy what is
     * required in the tests of this class.
     */
    private Event setUpStubEvent() {
        final List<String> users = List.of("foo", "bar", "baz");
        final List<String> emails = List.of("john@doe.com", "jane@doe.com");

        final Recipient recipients = new Recipient();
        recipients.setIgnoreUserPreferences(true);
        recipients.setOnlyAdmins(true);
        recipients.setUsers(users);
        recipients.setEmails(emails);

        Action action = createPoliciesAction(RandomStringUtils.secure().nextNumeric(6), "rhel", "policies", RandomStringUtils.secure().nextAlphanumeric(10));
        action.setRecipients(List.of(recipients));

        final EventWrapper<Action, EventTypeKeyBundleAppEventTriplet> eventWrapper = new EventWrapperAction(action);

        final Bundle bundle = new Bundle();
        bundle.setName("email-processor-test-bundle-name");
        final Application application = new Application();
        application.setBundle(bundle);
        application.setName("email-processor-test-application-name");

        final EventType eventType = new EventType();
        eventType.setApplication(application);
        eventType.setName("email-processor-test-event-type");

        final Event event = new Event();
        event.setId(UUID.randomUUID());
        event.setEventWrapper(eventWrapper);
        event.setEventType(eventType);
        event.setOrgId("email-processor-test-event-type-org-id");

        return event;
    }

    /**
     * Creates three very specific stub endpoints. The goal of having these
     * very specific endpoints created is to satisfy what is required in the
     * {@link EmailProcessorTest#testExtractAndTransformRecipientSettings()}
     * test.
     * @return <ul>
     * <li>A first endpoint with the "ignore user preferences" flag set to
     * true, and the "admin only" flag set to false, and with a group UUID set.
     * </li>
     * <li>A second endpoint with both the "ignore user preferences" and "admin
     * only" flags set to true, and with a group UUID set.</li>
     * <li>A third endpoint without a group UUID set, and both "ignore user
     * preferences" and "admin only" flags set to false./li>
     * </ul>
     */
    private List<Endpoint> setUpStubEndpoints() {
        // Create the first properties and the first endpoint.
        final SystemSubscriptionProperties systemSubscriptionProperties = new SystemSubscriptionProperties();
        systemSubscriptionProperties.setGroupId(UUID.randomUUID());
        systemSubscriptionProperties.setIgnorePreferences(true);
        systemSubscriptionProperties.setOnlyAdmins(false);

        final Endpoint endpoint = new Endpoint();
        endpoint.setProperties(systemSubscriptionProperties);

        // Create the second properties and the second endpoint.
        final SystemSubscriptionProperties systemSubscriptionProperties2 = new SystemSubscriptionProperties();
        systemSubscriptionProperties2.setGroupId(UUID.randomUUID());
        systemSubscriptionProperties2.setIgnorePreferences(true);
        systemSubscriptionProperties2.setOnlyAdmins(true);

        final Endpoint endpoint2 = new Endpoint();
        endpoint2.setProperties(systemSubscriptionProperties2);

        // Create the third properties and the third endpoint.
        final SystemSubscriptionProperties systemSubscriptionProperties3 = new SystemSubscriptionProperties();
        systemSubscriptionProperties3.setIgnorePreferences(false);
        systemSubscriptionProperties3.setOnlyAdmins(false);

        final Endpoint endpoint3 = new Endpoint();
        endpoint3.setProperties(systemSubscriptionProperties3);

        return List.of(endpoint, endpoint2, endpoint3);
    }

    /**
     * The common elements to all tests that need to be taken care of. In this
     * particular case:
     *
     * <ul>
     *     <li>The {@link TemplateRepository#isEmailAggregationSupported(UUID)}
     *     always returns {code false}, because we do not want to hit the
     *     database, and the goal of this tests class is not to test that class'
     *     function.
     *     </li>
     * </ul>
     */
    @BeforeEach
    void beforeEachTest() {
        Mockito.when(this.templateRepository.isEmailAggregationSupported(any(UUID.class))).thenReturn(false);
    }

    /**
     * Tests that the function under test is able to extract the recipient
     * settings from the stubbed event and endpoints' list.
     */
    @Test
    void testExtractAndTransformRecipientSettings() {
        final Event event = this.setUpStubEvent();
        final List<Endpoint> endpoints = this.setUpStubEndpoints();

        // Call the function under test.
        final Set<RecipientSettings> resultSet = this.emailProcessor.extractAndTransformRecipientSettings(event, endpoints);

        // Assert that the generated recipient settings contain the right
        // values.
        for (final RecipientSettings recipientSettings : resultSet) {
            // When the users are empty we know that the recipient settings
            // were generated from the endpoints. Otherwise, they were
            // generated from the event.
            if (recipientSettings.getUsers().isEmpty()) {
                // When the group ID is null, we know we have the third
                // endpoint in our hands, which should have both flags set to
                // false.
                if (recipientSettings.getGroupUUID() == null) {
                    Assertions.assertFalse(recipientSettings.isAdminsOnly(), String.format("the created recipient settings should have the \"admins only\" flag set to false: %s", recipientSettings));
                    Assertions.assertFalse(recipientSettings.isIgnoreUserPreferences(), String.format("the created recipient settings should have the \"ignore user preferences\" flag set to false: %s", recipientSettings));
                } else {
                    // In this case we need to find which endpoint the
                    // recipient settings correspond to, in order to compare
                    // the flags properly.
                    final Optional<Endpoint> endpoint = endpoints.stream()
                        .filter(e -> recipientSettings.getGroupUUID().equals(e.getProperties(SystemSubscriptionProperties.class).getGroupId()))
                        .findAny();

                    if (endpoint.isEmpty()) {
                        Assertions.fail("unable to find the endpoint with the specified group");
                    }

                    final SystemSubscriptionProperties properties = endpoint.get().getProperties(SystemSubscriptionProperties.class);

                    Assertions.assertEquals(properties.isIgnorePreferences(), recipientSettings.isIgnoreUserPreferences(), "the \"ignore user preferences\" flag in the recipient settings does not match the same flag in the endpoint's properties");
                    Assertions.assertEquals(properties.isOnlyAdmins(), recipientSettings.isAdminsOnly(), "the \"admins only\" flag in the recipient settings does not match the same flag in the endpoint's properties");
                }
            } else {
                Assertions.assertTrue(recipientSettings.isAdminsOnly(), "the \"admins only\" flag in the recipient settings does not match the same flag from the event");
                Assertions.assertTrue(recipientSettings.isIgnoreUserPreferences(), "the \"ignore user preferences\" flag in the recipient settings does not match the same flag from the event");
            }
        }
    }

    /**
     * Tests that when there is no associated email template for the given
     * event, then the processor ignores the event.
     */
    @Test
    void testMissingEmailTemplate() {
        // Prepare the required stubs.
        final Event event = this.setUpStubEvent();
        final List<Endpoint> endpoints = this.setUpStubEndpoints();

        // Simulate that there is no email template available for the event.
        Mockito.when(this.templateRepository.findInstantEmailTemplate(event.getEventType().getId())).thenReturn(Optional.empty());

        // Call the processor under test.
        this.emailProcessor.process(event, endpoints);

        // Verify that the processor returned without calling any further
        // dependencies in the code.
        Mockito.verify(this.templateService, Mockito.times(0)).compileTemplate(Mockito.anyString(), Mockito.anyString());
        Mockito.verify(this.templateService, Mockito.times(0)).renderTemplate(any(), any(TemplateInstance.class));
        Mockito.verify(this.endpointRepository, Mockito.times(0)).getOrCreateDefaultSystemSubscription(Mockito.anyString(), Mockito.anyString(), eq(EndpointType.EMAIL_SUBSCRIPTION));
        Mockito.verify(this.connectorSender, Mockito.times(0)).send(any(Event.class), any(Endpoint.class), any(JsonObject.class));
    }

    /**
     * Tests that when the subscribers list to the event is empty, and the
     * extracted recipient settings from both the event and the endpoints do
     * not contain a single "ignore user preferences" flag set to true, then
     * the processor ignores the event.
     */
    @Test
    void testIgnoreUserPreferencesEmptySubscribers() {
        // Prepare the required stubs.
        final Event event = this.setUpStubEvent();

        // Set the "ignore user preferences" to false, so that one of the
        // conditions to remove the resulting recipient settings from the set
        // in the email processor is met.
        final EventWrapper<Action, EventTypeKeyBundleAppEventTriplet> eventWrapper = (EventWrapper<Action, EventTypeKeyBundleAppEventTriplet>) event.getEventWrapper();
        final List<Recipient> recipientsMaybe = eventWrapper.getEvent().getRecipients();
        if (recipientsMaybe.isEmpty()) {
            Assertions.fail("the \"recipients\" object from the stubbed event is empty");
        }
        final Recipient recipients = recipientsMaybe.get(0);
        recipients.setIgnoreUserPreferences(false);

        final List<Endpoint> endpoints = this.setUpStubEndpoints();

        // Set all the user preferences' "ignore user preferences" flag to
        // false, so that one of the conditions to remove the resulting
        // recipient settings from the set in the email processor is met.
        for (final Endpoint endpoint : endpoints) {
            final SystemSubscriptionProperties properties = endpoint.getProperties(SystemSubscriptionProperties.class);
            properties.setIgnorePreferences(false);
        }

        // Return a non-empty instant email template to simulate that there
        // exists one for the event, in order to keep going with the execution.
        Mockito.when(this.templateRepository.findInstantEmailTemplate(event.getEventType().getId())).thenReturn(Optional.of(new InstantEmailTemplate()));

        // Do not return any subscribers for this test, so that the other
        // condition to remove the resulting recipient settings from the set
        // in the email processor is met.
        Mockito.when(this.subscriptionRepository.getSubscribers(event.getOrgId(), event.getEventType().getId(), SubscriptionType.INSTANT)).thenReturn(List.of());

        // Call the processor under test.
        this.emailProcessor.process(event, endpoints);

        // Verify that the processor returned without calling any further
        // dependencies in the code.
        Mockito.verify(this.templateService, Mockito.times(0)).compileTemplate(Mockito.anyString(), Mockito.anyString());
        Mockito.verify(this.templateService, Mockito.times(0)).renderTemplate(any(), any(TemplateInstance.class));
        Mockito.verify(this.endpointRepository, Mockito.times(0)).getOrCreateDefaultSystemSubscription(Mockito.anyString(), Mockito.anyString(), eq(EndpointType.EMAIL_SUBSCRIPTION));
        Mockito.verify(this.connectorSender, Mockito.times(0)).send(any(Event.class), any(Endpoint.class), any(JsonObject.class));
    }

    /**
     * Tests that when everything goes right, then the connector receives the
     * correct payload.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testSuccess(final boolean useCommonQuteTemplateModule) {
        Mockito.when(this.engineConfig.isUseCommonTemplateModuleToRenderEmailsEnabled()).thenReturn(useCommonQuteTemplateModule);
        Mockito.when(this.engineConfig.isDefaultTemplateEnabled()).thenReturn(true);
        Mockito.when(this.quteTemplateService.isDefaultEmailTemplateEnabled()).thenReturn(true);
        quteTemplateService.init();

        // Prepare the required stubs.
        final Event event = this.setUpStubEvent();
        final List<Endpoint> endpoints = this.setUpStubEndpoints();

        Mockito.when(this.templateRepository.findInstantEmailTemplate(event.getEventType().getId())).thenCallRealMethod();

        // Mock a list of subscribers that simulate the ones that should be
        // notified for the event.
        final List<String> subscribers = List.of("subscriber-a", "subscriber-b", "subscriber-c");
        Mockito.when(this.subscriptionRepository.getSubscribers(event.getOrgId(), event.getEventType().getId(), SubscriptionType.INSTANT)).thenReturn(subscribers);

        // Mock the endpoint that should get pulled from the database using
        // the endpoint repository.
        final Endpoint endpoint = new Endpoint();
        endpoint.setId(UUID.randomUUID());
        Mockito.when(this.endpointRepository.getOrCreateDefaultSystemSubscription(event.getAccountId(), event.getOrgId(), EndpointType.EMAIL_SUBSCRIPTION)).thenReturn(endpoint);

        // Mock the sender and the default recipients of the email
        final String stubbedSender = "Red Hat Insights noreply@redhat.com";
        Mockito.when(this.emailActorsResolver.getEmailSender(any())).thenReturn(stubbedSender);

        // Call the processor under test.
        this.emailProcessor.process(event, endpoints);

        // Verify that the compilation functions were called.
        Mockito.verify(this.templateService, Mockito.times(1)).compileTemplate(anyString(), eq("subject"));
        Mockito.verify(this.templateService, Mockito.times(1)).compileTemplate(anyString(), eq("body"));

        // Verify that the rendering functions were called.
        Mockito.verify(this.templateService, Mockito.times(1)).renderTemplate(eq(event.getEventWrapper().getEvent()), any(TemplateInstance.class));
        Mockito.verify(this.templateService, Mockito.times(2)).renderEmailBodyTemplate(eq(event.getEventWrapper().getEvent()), any(TemplateInstance.class), Mockito.isNull(), Mockito.anyBoolean());

        if (useCommonQuteTemplateModule) {
            Mockito.verify(this.quteTemplateService, Mockito.times(2)).renderTemplateWithCustomDataMap(any(TemplateDefinition.class), anyMap());
        } else {
            Mockito.verify(this.quteTemplateService, Mockito.never()).renderTemplateWithCustomDataMap(any(TemplateDefinition.class), anyMap());
        }

        // Verify that the endpoint repository was called to fetch the result
        // endpoint.
        Mockito.verify(this.endpointRepository, Mockito.times(1)).getOrCreateDefaultSystemSubscription(event.getAccountId(), event.getOrgId(), EndpointType.EMAIL_SUBSCRIPTION);

        // Verify that the connector was called with the right parameters.
        final ArgumentCaptor<Event> capturedEvent = ArgumentCaptor.forClass(Event.class);
        final ArgumentCaptor<Endpoint> capturedEndpoint = ArgumentCaptor.forClass(Endpoint.class);
        final ArgumentCaptor<JsonObject> capturedPayload = ArgumentCaptor.forClass(JsonObject.class);
        Mockito.verify(this.connectorSender, Mockito.times(1)).send(capturedEvent.capture(), capturedEndpoint.capture(), capturedPayload.capture());

        Assertions.assertEquals(event, capturedEvent.getValue(), "the captured event does not match with the stubbed one");
        Assertions.assertEquals(endpoint, capturedEndpoint.getValue(), "the captured endpoint does not match with the stubbed one");

        final JsonObject payload = capturedPayload.getValue();
        final String resultEmailBody = payload.getString("email_body");
        final String resultEmailSubject = payload.getString("email_subject");
        final String resultOrgId = payload.getString("orgId");
        final String resultEmailSender = payload.getString("email_sender");
        final Set<String> resultSubscribers = payload.getJsonArray("subscribers").stream().map(String.class::cast).collect(toSet());
        final Set<RecipientSettings> resultRecipientSettings = payload.getJsonArray("recipient_settings")
            .stream()
            .map(JsonObject.class::cast)
            .map(jsonRs -> {
                final String groupUUID = jsonRs.getString("group_uuid");

                return new RecipientSettings(
                    jsonRs.getBoolean("admins_only"),
                    jsonRs.getBoolean("ignore_user_preferences"),
                    (groupUUID == null) ? null : UUID.fromString(groupUUID),
                    jsonRs.getJsonArray("users").stream().map(String.class::cast).collect(toSet()),
                    jsonRs.getJsonArray("emails").stream().map(String.class::cast).collect(toSet())
                );
            }).collect(toSet());

        Assertions.assertTrue(resultEmailBody.contains("<p>You are receiving this email because the email template associated with this event type is not configured properly.</p>"), "the rendered email's body from the email notification does not match with expectation");
        Assertions.assertTrue(resultEmailSubject.contains("rhel/policies/policy-triggered triggered"), "the rendered email's subject from the email notification does not match with expectation");
        Assertions.assertEquals(stubbedSender, resultEmailSender, "the rendered email's sender from the email notification does not match the stubbed sender");
        Assertions.assertEquals(event.getOrgId(), resultOrgId, "the organization ID from the email notification does not match the one set in the stubbed event");
        Assertions.assertEquals(Set.copyOf(subscribers), resultSubscribers, "the subscribers set in the email notification do not match the stubbed ones");

        final Set<RecipientSettings> recipientSettings = this.emailProcessor.extractAndTransformRecipientSettings(event, endpoints);
        Assertions.assertIterableEquals(recipientSettings, resultRecipientSettings, "the recipient settings set in the email notification do not match the stubbed ones");
    }
}
