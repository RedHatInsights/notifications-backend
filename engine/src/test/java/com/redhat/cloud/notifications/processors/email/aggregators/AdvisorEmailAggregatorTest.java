package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.AdvisorTestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Event;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeClass;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;

/*
 * Aggregated data should be of the form:
 * {
    "new_recommendations": {
        "product_eol_check|NGINX_EOL_ERROR": {
            "description": "Red Hat has discontinued support services as well as software maintenance services for the End-Of-Life nginx",
            "total_risk": 4,
            "has_incident": false,
            "url": "https://console.redhat.com/insights/advisor/recommendations/product_eol_check|NGINX_EOL_ERROR",
            "affected_systems": 33
        },
        "container_rhel_version_privileged|CONTAINER_RHEL_VERSION_PRIVILEGED": {
            "description": "A privileged container running on a different RHEL version host is not compatible",
            "total_risk": 4,
            "has_incident": true,
            "url": "https://console.redhat.com/insights/advisor/recommendations/container_rhel_version_privileged|CONTAINER_RHEL_VERSION_PRIVILEGED",
            "affected_systems": 3
        }
    },
    "resolved_recommendations": {
        "auditd_daemon_log_file_oversize|AUDIT_DAEMON_LOG_FILE_OVERSIZE": {
            "description": "The auditd service gets suspended when the size of the log file is over \"max_log_file\"",
            "total_risk": 2,
            "has_incident": true,
            "url": "https://console.redhat.com/insights/advisor/recommendations/audit_daemon_log_file_oversize|AUDIT_DAEMON_LOG_FILE_OVERSIZE",
            "affected_systems": 1
        }
    },
    "deactivated_recommendations": {
        "el6_to_el7_upgrade|RHEL6_TO_RHEL7_UPGRADE_AVAILABLE_V4": {
            "description": "RHEL 6 system is eligible for an in-place upgrade to RHEL 7 using the Leapp utility",
            "has_incident": false,
            "url": "https://console.redhat.com/insights/advisor/recommendations/el6_to_el7_upgrade|RHEL6_TO_RHEL7_UPGRADE_AVAILABLE_V4",
            "total_risk": 3
        }
    }
}
 */
class AdvisorEmailAggregatorTest {

    public static final String BUNDLE_RHEL = "rhel";
    public static final String BUNDLE_OPENSHIFT = "openshift";
    public static final String APPLICATION_ADVISOR = "advisor";
    public static final String TEST_INVENTORY_ID = "00112233-4455-6677-8899-AABBCCDDEE01";
    public static final String TEST_INVENTORY_NAME = "system01.example.org";
    public static final String TEST_RULE_1_ID = "test|Active_rule";
    public static final String TEST_RULE_1_DESCRIPTION = "Active rule";
    public static final String TEST_RULE_1_URL = "https://console.redhat.com/insights/advisor/recommendations/test|Active_rule";
    public static final boolean TEST_RULE_1_IMPACTING = false;
    public static final Integer TEST_RULE_1_TOTAL_RISK = 1;
    /* From Advisor test data - but here we don't care about whether this is
     * actually acked in the ruleset.  If we got a notification for it, we
     * aggregate it. */
    public static final String TEST_RULE_3_ID = "test|Acked_rule";
    public static final String TEST_RULE_3_DESCRIPTION = "Acked rule";
    public static final String TEST_RULE_3_URL = "https://console.redhat.com/insights/advisor/recommendations/test|Acked_rule";
    public static final boolean TEST_RULE_3_IMPACTING = false;
    public static final Integer TEST_RULE_3_TOTAL_RISK = 1;

    private AdvisorEmailAggregator aggregator;
    private Action rhelAdvisorAction;
    private Action openshiftAdvisorAction;
    private Event eventActiveRule;

    @BeforeClass
    void setUpTestData() {
        rhelAdvisorAction = AdvisorTestHelpers.createAction(
            BUNDLE_RHEL, APPLICATION_ADVISOR, TEST_INVENTORY_ID, TEST_INVENTORY_NAME
        );
        openshiftAdvisorAction = AdvisorTestHelpers.createAction(
            BUNDLE_OPENSHIFT, APPLICATION_ADVISOR, TEST_INVENTORY_ID, TEST_INVENTORY_NAME
        );
        eventActiveRule = AdvisorTestHelpers.createEvent(
            TEST_RULE_ID, TEST_RULE_DESCRIPTION, TEST_RULE_URL,
            TEST_RULE_IMPACTING, TEST_RULE_TOTAL_RISK
        );
    }

    @BeforeEach
    void setUp() {
        aggregator = new AdvisorEmailAggregator();
    }

    @Test
    void emptyAggregatorHasNoOrgId() {
        Assertions.assertNull(aggregator.getOrgId(), "Empty aggregator has no orgId");
    }

    @Test
    void shouldSetOrgId() {
        oneMessage = AdvisorTestHelpers.createEmailAggregation(emailActionMessage);
        aggregator.aggregate(oneMessage);
        Assertions.assertEquals(DEFAULT_ORG_ID, aggregator.getOrgId());
    }

    @Test
    void validatePayload() {
        oneMessage = AdvisorTestHelpers.createEmailAggregation(emailActionMessage);
        oneMessage.setEvents(List.of(eventActiveRule));
        aggregator.aggregate(oneMessage);

        Map<String, Object> context = aggregator.getContext();
        JsonObject Advisor = JsonObject.mapFrom(context).getJsonObject("Advisor");
        System.out.println(Advisor.toString());

        Assertions.assertFalse(Advisor.containsKey("foo"));
        Assertions.assertEquals(Advisor.getJsonArray("report-upload-failed").size(), 2);
        Assertions.assertEquals(Advisor.getJsonArray("Advisor-below-threshold").size(), 2);
        Assertions.assertEquals(Advisor.getJsonArray("Advisor-below-threshold").size(), 2);
        // Assertions.assertEquals(Advisor.getJsonArray("system-not-reporting").size(), 2);
    }
}
