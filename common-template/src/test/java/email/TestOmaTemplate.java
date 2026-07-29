package email;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.qute.templates.Severity;
import helpers.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.List;

import static helpers.TestHelpers.DEFAULT_ORG_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestOmaTemplate extends EmailTemplatesRendererHelper {

    private static final String PARTNERSHIP_REQUEST = "partnership-request";
    private static final String PARTNERSHIP_RESPONSE = "partnership-response";
    private static final String ASSESSMENT_SHARED = "assessment-shared";
    private static final String ASSESSMENT_CREATED = "assessment-created";

    @Override
    protected String getApp() {
        return "migration-advisor";
    }

    @Override
    protected String getBundle() {
        return "openshift";
    }

    @Override
    protected String getBundleDisplayName() {
        return "OpenShift";
    }

    @Override
    protected String getAppDisplayName() {
        return "Migration Advisor";
    }

    @Test
    public void testInstantEmailTitle() {
        eventTypeDisplayName = "Partnership Request";
        Action action = createOmaAction();

        String result = generateEmailSubject(PARTNERSHIP_REQUEST, action);
        assertEquals("Instant notification - Partnership Request - Migration Advisor - OpenShift", result);

        action.setSeverity(Severity.IMPORTANT.name());
        String severityResult = generateEmailSubject(PARTNERSHIP_REQUEST, action);
        assertEquals("[IMPORTANT] Instant notification - Partnership Request - Migration Advisor - OpenShift", severityResult);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testPartnershipRequestEmailBody(boolean useBetaTemplate) {
        Action action = createOmaAction();

        String result = generateEmailBody(PARTNERSHIP_REQUEST, action, useBetaTemplate);
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        assertTrue(result.contains("OpenShift - Migration Advisor"));
        assertTrue(result.contains("New Partnership Request"));
        assertTrue(result.contains("/openshift/migration-advisor/partnerships"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testPartnershipResponseAcceptedEmailBody(boolean useBetaTemplate) {
        Action action = createOmaActionWithDecision("Accepted", null);

        String result = generateEmailBody(PARTNERSHIP_RESPONSE, action, useBetaTemplate);
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        assertTrue(result.contains("OpenShift - Migration Advisor"));
        assertTrue(result.contains("Update on Your Partnership Request"));
        assertTrue(result.contains("Accepted"));
        assertFalse(result.contains("Reason for decision"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testPartnershipResponseDeclinedEmailBody(boolean useBetaTemplate) {
        Action action = createOmaActionWithDecision("Declined", "Not a good fit at this time");

        String result = generateEmailBody(PARTNERSHIP_RESPONSE, action, useBetaTemplate);
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        assertTrue(result.contains("Declined"));
        assertTrue(result.contains("Reason for decision"));
        assertTrue(result.contains("Not a good fit at this time"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testAssessmentSharedEmailBody(boolean useBetaTemplate) {
        Action action = createOmaActionWithAssessment(ASSESSMENT_SHARED, "assessment-123");

        String result = generateEmailBody(ASSESSMENT_SHARED, action, useBetaTemplate);
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        assertTrue(result.contains("OpenShift - Migration Advisor"));
        assertTrue(result.contains("New Assessment Shared"));
        assertTrue(result.contains("/openshift/migration-advisor/assessments/assessment-123"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testAssessmentCreatedEmailBody(boolean useBetaTemplate) {
        Action action = createOmaActionWithAssessment(ASSESSMENT_CREATED, "assessment-456");

        String result = generateEmailBody(ASSESSMENT_CREATED, action, useBetaTemplate);
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        assertTrue(result.contains("OpenShift - Migration Advisor"));
        assertTrue(result.contains("New Assessment Created"));
        assertTrue(result.contains("/openshift/migration-advisor/assessments/assessment-456"));
    }

    private static Action createOmaAction() {
        Action action = new Action();
        action.setBundle("openshift");
        action.setApplication("migration-advisor");
        action.setTimestamp(LocalDateTime.now());
        action.setEventType(PARTNERSHIP_REQUEST);
        action.setOrgId(DEFAULT_ORG_ID);
        action.setContext(new Context.ContextBuilder().build());
        action.setEvents(List.of(
            new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(new Payload.PayloadBuilder().build())
                .build()
        ));
        return action;
    }

    private static Action createOmaActionWithDecision(String decision, String reason) {
        Action action = createOmaAction();
        action.setEventType(PARTNERSHIP_RESPONSE);
        Context.ContextBuilder contextBuilder = new Context.ContextBuilder();
        contextBuilder.withAdditionalProperty("decision", decision);
        if (reason != null) {
            contextBuilder.withAdditionalProperty("reason", reason);
        }
        action.setContext(contextBuilder.build());
        return action;
    }

    private static Action createOmaActionWithAssessment(String eventType, String assessmentId) {
        Action action = createOmaAction();
        action.setEventType(eventType);
        action.setContext(
            new Context.ContextBuilder()
                .withAdditionalProperty("assessment_id", assessmentId)
                .build()
        );
        return action;
    }
}
