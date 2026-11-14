package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.Severity;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.helpers.ResourceHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Parser;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.ingress.Recipient;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.EventAggregationCriterion;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class EmailDataAggregatorTest {

    private static final String ORG_ID = "data-aggregator-test-org";
    private static final String BUNDLE = "rhel";
    private static final String COMPLIANCE_APP = "compliance";
    private static final String UNSUPPORTED_APP = "advisor";

    @Inject
    EmailDataAggregator emailDataAggregator;

    @Inject
    ResourceHelpers resourceHelpers;

    @AfterEach
    void afterEach() {
        resourceHelpers.purgeEventAggregations();
        resourceHelpers.purgeEndpoints();
    }

    @Test
    void shouldFoldEventsAndTagEachWithItsOwnSeverity() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        resourceHelpers.addEventEmailAggregation(ORG_ID, BUNDLE, COMPLIANCE_APP, now.minusHours(2),
            buildCompliancePayload("report-upload-failed", "policy-1"), Severity.CRITICAL, false);
        resourceHelpers.addEventEmailAggregation(ORG_ID, BUNDLE, COMPLIANCE_APP, now.minusHours(1),
            buildCompliancePayload("report-upload-failed", "policy-2"), Severity.LOW, false);

        EventAggregationCriterion criterion = criterionFor(COMPLIANCE_APP);
        Map<String, Object> aggregatedData = emailDataAggregator.getAggregatedData(criterion, now.minusDays(1), now.plusMinutes(1));

        JsonArray reportUploadFailed = JsonObject.mapFrom(aggregatedData)
            .getJsonObject("compliance")
            .getJsonArray("report-upload-failed");

        assertEquals(2, reportUploadFailed.size());
        assertEquals("CRITICAL", reportUploadFailed.getJsonObject(0).getString("_severity"));
        assertEquals("LOW", reportUploadFailed.getJsonObject(1).getString("_severity"));
    }

    @Test
    void shouldTagWithEventTypeNameAndRawRecipientsOverride() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        resourceHelpers.addEventEmailAggregation(ORG_ID, BUNDLE, COMPLIANCE_APP, now.minusHours(1),
            buildCompliancePayloadWithRecipientsOverride("report-upload-failed", "policy-1"), Severity.LOW, false);

        EventAggregationCriterion criterion = criterionFor(COMPLIANCE_APP);
        Map<String, Object> aggregatedData = emailDataAggregator.getAggregatedData(criterion, now.minusDays(1), now.plusMinutes(1));

        JsonObject item = JsonObject.mapFrom(aggregatedData)
            .getJsonObject("compliance")
            .getJsonArray("report-upload-failed")
            .getJsonObject(0);

        // "event_type_test" -- the DB EventType.name ResourceHelpers.addEventEmailAggregation() always
        // uses for test fixtures, not the "report-upload-failed" string embedded in the raw payload
        // JSON -- `_eventType` tags the real persisted EventType's name, same as production.
        assertEquals("event_type_test", item.getString("_eventType"));
        // Verbatim-copy contract: aggregator doesn't parse/interpret this array, just forwards it, so
        // only its presence/size is asserted here, not its internal field names.
        JsonArray recipientsOverride = item.getJsonArray("_recipientsOverride");
        assertEquals(1, recipientsOverride.size());
    }

    @Test
    void shouldReturnNullWhenNoAggregatorSupportsTheApp() {
        EventAggregationCriterion criterion = criterionFor(UNSUPPORTED_APP);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        assertNull(emailDataAggregator.getAggregatedData(criterion, now.minusDays(1), now));
    }

    @Test
    void shouldReturnEmptySkeletonWhenNothingToAggregate() {
        // Matches engine's current behavior: ComplianceEmailAggregator doesn't override isEmpty(),
        // so an app with nothing pending still yields its empty skeleton, not null.
        EventAggregationCriterion criterion = criterionFor(COMPLIANCE_APP);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        Map<String, Object> aggregatedData = emailDataAggregator.getAggregatedData(criterion, now.minusDays(1), now);
        JsonObject compliance = JsonObject.mapFrom(aggregatedData).getJsonObject("compliance");

        assertEquals(0, compliance.getJsonArray("report-upload-failed").size());
        assertEquals(0, compliance.getJsonArray("compliance-below-threshold").size());
    }

    private EventAggregationCriterion criterionFor(String applicationName) {
        Application application = resourceHelpers.findOrCreateApplication(BUNDLE, applicationName);
        return new EventAggregationCriterion(ORG_ID, application.getBundleId(), application.getId(), BUNDLE, applicationName);
    }

    private static String buildCompliancePayload(String eventType, String policyId) {
        Action action = new Action();
        action.setBundle(BUNDLE);
        action.setApplication(COMPLIANCE_APP);
        action.setEventType(eventType);
        action.setTimestamp(LocalDateTime.now());
        action.setOrgId(ORG_ID);
        action.setContext(new Context.ContextBuilder().build());
        action.setEvents(List.of(
            new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(new Payload.PayloadBuilder().withAdditionalProperty("policy_id", policyId).build())
                .build()
        ));

        return Parser.encode(action);
    }

    private static String buildCompliancePayloadWithRecipientsOverride(String eventType, String policyId) {
        Action action = new Action();
        action.setBundle(BUNDLE);
        action.setApplication(COMPLIANCE_APP);
        action.setEventType(eventType);
        action.setTimestamp(LocalDateTime.now());
        action.setOrgId(ORG_ID);
        action.setContext(new Context.ContextBuilder().build());
        action.setRecipients(List.of(new Recipient.RecipientBuilder().withOnlyAdmins(true).build()));
        action.setEvents(List.of(
            new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(new Payload.PayloadBuilder().withAdditionalProperty("policy_id", policyId).build())
                .build()
        ));

        return Parser.encode(action);
    }
}
