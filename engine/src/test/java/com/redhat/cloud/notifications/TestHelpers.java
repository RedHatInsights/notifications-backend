package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Parser;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.transformers.BaseTransformer;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;

public class TestHelpers {

    public static final String HCC_LOGO_TARGET = "Logo-Red_Hat-Hybrid_Cloud_Console-A-Reverse-RGB";

    public static BaseTransformer baseTransformer = new BaseTransformer();
    public static final String policyId1 = "abcd-efghi-jkl-lmn";
    public static final String policyName1 = "Foobar";
    public static final String policyId2 = "0123-456-789-5721f";
    public static final String policyName2 = "Latest foo is installed";
    public static final String eventType = "test-email-subscription-instant";

    public static EmailAggregation createEmailAggregation(String orgId, String bundle, String application, String policyId, String inventory_id) {
        EmailAggregation aggregation = new EmailAggregation();
        aggregation.setBundleName(bundle);
        aggregation.setApplicationName(application);
        aggregation.setOrgId(orgId);

        Action emailActionMessage = new Action();
        emailActionMessage.setBundle(bundle);
        emailActionMessage.setApplication(application);
        emailActionMessage.setTimestamp(LocalDateTime.now());
        emailActionMessage.setEventType(eventType);

        emailActionMessage.setContext(
                new Context.ContextBuilder()
                        .withAdditionalProperty("inventory_id", inventory_id)
                        .withAdditionalProperty("system_check_in", "2020-08-03T15:22:42.199046")
                        .withAdditionalProperty("display_name", "My test machine")
                        .withAdditionalProperty("tags", List.of())
                        .build()
        );
        emailActionMessage.setEvents(List.of(
                new Event.EventBuilder()
                        .withMetadata(new Metadata.MetadataBuilder().build())
                        .withPayload(
                                new Payload.PayloadBuilder()
                                        .withAdditionalProperty("policy_id", policyId)
                                        .withAdditionalProperty("policy_name", "not-tested-name")
                                        .withAdditionalProperty("policy_description", "not-used-desc")
                                        .withAdditionalProperty("policy_condition", "not-used-condition")
                                        .build()
                        )
                        .build()
        ));

        emailActionMessage.setOrgId(orgId);

        JsonObject payload = baseTransformer.toJsonObject(emailActionMessage);
        aggregation.setPayload(payload);

        return aggregation;
    }

    public static String serializeAction(Action action) {
        return Parser.encode(action);
    }

    public static Action createPoliciesAction(String accountId, String bundle, String application, String hostDisplayName) {
        Action emailActionMessage = new Action();
        emailActionMessage.setBundle(bundle);
        emailActionMessage.setApplication(application);
        emailActionMessage.setTimestamp(LocalDateTime.of(2020, 10, 3, 15, 22, 13, 25));
        emailActionMessage.setEventType(eventType);
        emailActionMessage.setRecipients(List.of());

        emailActionMessage.setContext(
            new Context.ContextBuilder()
                    .withAdditionalProperty("inventory_id", "host-01")
                    .withAdditionalProperty("system_check_in", "2020-08-03T15:22:42.199046")
                    .withAdditionalProperty("display_name", hostDisplayName)
                    .withAdditionalProperty("tags", List.of())
                    .build()
        );
        emailActionMessage.setEvents(List.of(
                new Event.EventBuilder()
                        .withMetadata(new Metadata.MetadataBuilder().build())
                        .withPayload(
                                new Payload.PayloadBuilder()
                                        .withAdditionalProperty("policy_id", policyId1)
                                        .withAdditionalProperty("policy_name", policyName1)
                                        .withAdditionalProperty("policy_description", "not-used-desc")
                                        .withAdditionalProperty("policy_condition", "not-used-condition")
                                        .build()
                        )
                        .build(),
                new Event.EventBuilder()
                        .withMetadata(new Metadata.MetadataBuilder().build())
                        .withPayload(
                                new Payload.PayloadBuilder()
                                        .withAdditionalProperty("policy_id", policyId2)
                                        .withAdditionalProperty("policy_name", policyName2)
                                        .withAdditionalProperty("policy_description", "not-used-desc")
                                        .withAdditionalProperty("policy_condition", "not-used-condition")
                                        .build()
                        )
                        .build()
        ));

        emailActionMessage.setAccountId(accountId);
        emailActionMessage.setOrgId(DEFAULT_ORG_ID);

        return emailActionMessage;
    }

    public static Action createAdvisorAction(String accountId, String eventType) {
        Action emailActionMessage = new Action();
        emailActionMessage.setBundle("rhel");
        emailActionMessage.setApplication("advisor");
        emailActionMessage.setTimestamp(LocalDateTime.of(2020, 10, 3, 15, 22, 13, 25));
        emailActionMessage.setEventType(eventType);
        emailActionMessage.setAccountId(accountId);
        emailActionMessage.setOrgId(DEFAULT_ORG_ID);

        if (eventType.equals("deactivated-recommendation")) {
            emailActionMessage.setContext(new Context.ContextBuilder().build());
            emailActionMessage.setEvents(List.of(
                    new Event.EventBuilder()
                            .withMetadata(new Metadata.MetadataBuilder().build())
                            .withPayload(
                                    new Payload.PayloadBuilder()
                                            .withAdditionalProperty("rule_id", "retire-rule1")
                                            .withAdditionalProperty("rule_description", "Rule being deactivated for retirement")
                                            .withAdditionalProperty("total_risk", 1)
                                            .withAdditionalProperty("affected_systems", 1)
                                            .withAdditionalProperty("deactivation_reason", "Retirement")
                                            .build()
                            )
                            .build(),
                    new Event.EventBuilder()
                            .withMetadata(new Metadata.MetadataBuilder().build())
                            .withPayload(
                                    new Payload.PayloadBuilder()
                                            .withAdditionalProperty("rule_id", "enhance-rule2")
                                            .withAdditionalProperty("rule_description", "Rule being deactivated for enhancement")
                                            .withAdditionalProperty("total_risk", 2)
                                            .withAdditionalProperty("affected_systems", 2)
                                            .withAdditionalProperty("deactivation_reason", "Enhancement")
                                            .build()
                            )
                            .build()
            ));
        } else {
            emailActionMessage.setContext(
                    new Context.ContextBuilder()
                            .withAdditionalProperty("inventory_id", "host-01")
                            .withAdditionalProperty("hostname", "my-host")
                            .withAdditionalProperty("display_name", "My Host")
                            .withAdditionalProperty("rhel_version", "8.3")
                            .withAdditionalProperty("host_url", "this-is-my-host-url")
                            .build()
            );
            emailActionMessage.setEvents(List.of(
                    new Event.EventBuilder()
                            .withMetadata(new Metadata.MetadataBuilder().build())
                            .withPayload(
                                    new Payload.PayloadBuilder()
                                            .withAdditionalProperty("rule_id", "rule-id-low-001")
                                            .withAdditionalProperty("rule_description", "nice rule with low risk")
                                            .withAdditionalProperty("total_risk", "1")
                                            .withAdditionalProperty("publish_date", "2020-08-03T15:22:42.199046")
                                            .withAdditionalProperty("report_url", "http://the-report-for-rule-id-low-001")
                                            .withAdditionalProperty("rule_url", "http://the-rule-id-low-001")
                                            .build()
                            )
                            .build(),
                    new Event.EventBuilder()
                            .withMetadata(new Metadata.MetadataBuilder().build())
                            .withPayload(
                                    new Payload.PayloadBuilder()
                                            .withAdditionalProperty("rule_id", "rule-id-moderate-001")
                                            .withAdditionalProperty("rule_description", "nice rule with moderate risk")
                                            .withAdditionalProperty("total_risk", "2")
                                            .withAdditionalProperty("publish_date", "2020-08-03T15:22:42.199046")
                                            .withAdditionalProperty("report_url", "http://the-report-for-rule-id-moderate-001")
                                            .withAdditionalProperty("rule_url", "http://the-rule-id-moderate-001")
                                            .build()
                            )
                            .build(),
                    new Event.EventBuilder()
                            .withMetadata(new Metadata.MetadataBuilder().build())
                            .withPayload(
                                    new Payload.PayloadBuilder()
                                            .withAdditionalProperty("rule_id", "rule-id-important-001")
                                            .withAdditionalProperty("rule_description", "nice rule with important risk")
                                            .withAdditionalProperty("total_risk", "3")
                                            .withAdditionalProperty("publish_date", "2020-08-03T15:22:42.199046")
                                            .withAdditionalProperty("report_url", "http://the-report-for-rule-id-important-001")
                                            .withAdditionalProperty("rule_url", "http://the-rule-id-important-001")
                                            .build()
                            )
                            .build(),
                    new Event.EventBuilder()
                            .withMetadata(new Metadata.MetadataBuilder().build())
                            .withPayload(
                                    new Payload.PayloadBuilder()
                                            .withAdditionalProperty("rule_id", "rule-id-critical-001")
                                            .withAdditionalProperty("rule_description", "nice rule with critical risk")
                                            .withAdditionalProperty("total_risk", "4")
                                            .withAdditionalProperty("publish_date", "2020-08-03T15:22:42.199046")
                                            .withAdditionalProperty("report_url", "http://the-report-for-rule-id-critical-001")
                                            .withAdditionalProperty("rule_url", "http://the-rule-id-critical-001")
                                            .build()
                            )
                            .build()
            ));
        }
        return emailActionMessage;
    }

    public static Action createAdvisorOpenshiftAction(String accountId, String eventType) {
        Action emailActionMessage = new Action();
        emailActionMessage.setBundle("openshift");
        emailActionMessage.setApplication("advisor");
        emailActionMessage.setTimestamp(LocalDateTime.of(2021, 5, 20, 15, 22, 13, 25));
        emailActionMessage.setEventType(eventType);
        emailActionMessage.setAccountId(accountId);
        emailActionMessage.setOrgId(DEFAULT_ORG_ID);

        if (eventType.equals("new-recommendation")) {
            emailActionMessage.setContext(
                    new Context.ContextBuilder()
                            .withAdditionalProperty("display_name", "some-cluster-name")
                            .withAdditionalProperty("host_url", "some-ocm-url-to-the-cluster")
                            .build()
            );
            emailActionMessage.setEvents(List.of(
                    new Event.EventBuilder()
                        .withMetadata(new Metadata.MetadataBuilder().build())
                        .withPayload(
                                new Payload.PayloadBuilder()
                                        .withAdditionalProperty("rule_description", "nice rule with low risk")
                                        .withAdditionalProperty("total_risk", "1")
                                        .withAdditionalProperty("publish_date", "2020-08-03T15:22:42.199046")
                                        .withAdditionalProperty("rule_url", "http://the-rule-id-low-001")
                                        .build()
                        )
                        .build(),
                    new Event.EventBuilder()
                        .withMetadata(new Metadata.MetadataBuilder().build())
                        .withPayload(
                                new Payload.PayloadBuilder()
                                        .withAdditionalProperty("rule_description", "nice rule with moderate risk")
                                        .withAdditionalProperty("total_risk", "2")
                                        .withAdditionalProperty("publish_date", "2020-08-03T15:22:42.199046")
                                        .withAdditionalProperty("rule_url", "http://the-rule-id-moderate-001")
                                        .build()
                        )
                        .build(),
                    new Event.EventBuilder()
                        .withMetadata(new Metadata.MetadataBuilder().build())
                        .withPayload(
                                new Payload.PayloadBuilder()
                                        .withAdditionalProperty("rule_description", "nice rule with important risk")
                                        .withAdditionalProperty("total_risk", "3")
                                        .withAdditionalProperty("publish_date", "2020-08-03T15:22:42.199046")
                                        .withAdditionalProperty("rule_url", "http://the-rule-id-important-001")
                                        .build()
                        )
                        .build(),
                    new Event.EventBuilder()
                        .withMetadata(new Metadata.MetadataBuilder().build())
                        .withPayload(
                                new Payload.PayloadBuilder()
                                        .withAdditionalProperty("rule_description", "nice rule with critical risk")
                                        .withAdditionalProperty("total_risk", "4")
                                        .withAdditionalProperty("publish_date", "2020-08-03T15:22:42.199046")
                                        .withAdditionalProperty("rule_url", "http://the-rule-id-critical-001")
                                        .build()
                        )
                        .build()
            ));
        }
        return emailActionMessage;
    }

    public static Action createComplianceAction() {
        Action emailActionMessage = new Action();
        emailActionMessage.setBundle(StringUtils.EMPTY);
        emailActionMessage.setApplication(StringUtils.EMPTY);
        emailActionMessage.setTimestamp(LocalDateTime.of(2020, 10, 3, 15, 22, 13, 25));
        emailActionMessage.setEventType(eventType);
        emailActionMessage.setRecipients(List.of());

        emailActionMessage.setContext(
            new Context.ContextBuilder()
                .withAdditionalProperty("system_check_in", "2020-08-03T15:22:42.199046")
                .build()
        );

        emailActionMessage.setEvents(List.of(
            new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(
                    new Payload.PayloadBuilder()
                        .withAdditionalProperty("host_id", "host-01")
                        .withAdditionalProperty("host_name", "My test machine")
                        .withAdditionalProperty("policy_id", "Policy id 1")
                        .withAdditionalProperty("policy_name", "Tested name")
                        .withAdditionalProperty("compliance_score", "20")
                        .withAdditionalProperty("policy_threshold", "25")
                        .withAdditionalProperty("request_id", "12345")
                        .withAdditionalProperty("error", "Kernel panic (test)")
                        .build()
                )
                .build()
        ));

        emailActionMessage.setAccountId(StringUtils.EMPTY);
        emailActionMessage.setOrgId(DEFAULT_ORG_ID);

        return emailActionMessage;
    }

    public static Action createAnsibleAction(String slug) {
        Action emailActionMessage = new Action();
        emailActionMessage.setBundle(StringUtils.EMPTY);
        emailActionMessage.setApplication(StringUtils.EMPTY);
        emailActionMessage.setTimestamp(LocalDateTime.of(2020, 10, 3, 15, 22, 13, 25));
        emailActionMessage.setEventType(eventType);
        emailActionMessage.setRecipients(List.of());

        emailActionMessage.setContext(
            new Context.ContextBuilder()
                .withAdditionalProperty("system_check_in", "2020-08-03T15:22:42.199046")
                .withAdditionalProperty("slug", slug)
                .build()
        );

        emailActionMessage.setAccountId(StringUtils.EMPTY);
        emailActionMessage.setOrgId(DEFAULT_ORG_ID);

        return emailActionMessage;
    }

    public static Action createCostManagementAction() {
        Action emailActionMessage = new Action();
        emailActionMessage.setBundle(StringUtils.EMPTY);
        emailActionMessage.setApplication(StringUtils.EMPTY);
        emailActionMessage.setTimestamp(LocalDateTime.of(2020, 10, 3, 15, 22, 13, 25));
        emailActionMessage.setEventType(eventType);
        emailActionMessage.setRecipients(List.of());

        emailActionMessage.setContext(
            new Context.ContextBuilder()
                .withAdditionalProperty("system_check_in", "2020-08-03T15:22:42.199046")
                .withAdditionalProperty("source_name", "Dummy source name")
                .withAdditionalProperty("cost_model_name", "Sample model")
                .withAdditionalProperty("cost_model_id", "4540543DGE")
                .build()
        );

        emailActionMessage.setAccountId(StringUtils.EMPTY);
        emailActionMessage.setOrgId(DEFAULT_ORG_ID);

        return emailActionMessage;
    }

    public static Action createIntegrationsFailedAction() {
        Action emailActionMessage = new Action();
        emailActionMessage.setBundle(StringUtils.EMPTY);
        emailActionMessage.setApplication(StringUtils.EMPTY);
        emailActionMessage.setTimestamp(LocalDateTime.of(2020, 10, 3, 15, 22, 13, 25));
        emailActionMessage.setEventType(eventType);
        emailActionMessage.setRecipients(List.of());

        emailActionMessage.setContext(
            new Context.ContextBuilder()
                .withAdditionalProperty("system_check_in", "2020-08-03T15:22:42.199046")
                .withAdditionalProperty("failed-integration", "Failed integration")
                .build()
        );

        emailActionMessage.setEvents(List.of(
            new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(
                    new Payload.PayloadBuilder()
                        .withAdditionalProperty("outcome", "test outcome data")
                        .build()
                )
                .build()
        ));

        emailActionMessage.setAccountId(StringUtils.EMPTY);
        emailActionMessage.setOrgId(DEFAULT_ORG_ID);

        return emailActionMessage;
    }


    public static Action createSourcesAction() {
        Action emailActionMessage = new Action();
        emailActionMessage.setBundle(StringUtils.EMPTY);
        emailActionMessage.setApplication(StringUtils.EMPTY);
        emailActionMessage.setTimestamp(LocalDateTime.of(2020, 10, 3, 15, 22, 13, 25));
        emailActionMessage.setEventType(eventType);
        emailActionMessage.setRecipients(List.of());

        emailActionMessage.setContext(
            new Context.ContextBuilder()
                .withAdditionalProperty("source_id", 5)
                .withAdditionalProperty("resource_display_name", "test name 1")
                .withAdditionalProperty("previous_availability_status", "old status")
                .withAdditionalProperty("current_availability_status", "current status")
                .withAdditionalProperty("source_name", "test source name 1")
                .build()
        );

        emailActionMessage.setAccountId(StringUtils.EMPTY);
        emailActionMessage.setOrgId(DEFAULT_ORG_ID);

        return emailActionMessage;
    }

    public static Action createMalwareDetectionAction() {
        Action emailActionMessage = new Action();
        emailActionMessage.setBundle(StringUtils.EMPTY);
        emailActionMessage.setApplication(StringUtils.EMPTY);
        emailActionMessage.setTimestamp(LocalDateTime.of(2020, 10, 3, 15, 22, 13, 25));
        emailActionMessage.setEventType(eventType);
        emailActionMessage.setRecipients(List.of());

        emailActionMessage.setContext(
            new Context.ContextBuilder()
                .withAdditionalProperty("system_check_in", "2020-08-03T15:22:42.199046")
                .build()
        );

        emailActionMessage.setEvents(List.of(
            new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(
                    new Payload.PayloadBuilder()
                        .withAdditionalProperty("host_id", "host-01")
                        .withAdditionalProperty("host_name", "My test machine")
                        .withAdditionalProperty("matched_rules", Arrays.asList("rule 1", "rule 2"))
                        .withAdditionalProperty("matched_at", "2020-08-03T15:22:42.199046")
                        .build()
                )
                .build()
        ));

        emailActionMessage.setAccountId(StringUtils.EMPTY);
        emailActionMessage.setOrgId(DEFAULT_ORG_ID);

        return emailActionMessage;
    }

    public static Action createEdgeManagementAction() {
        Action emailActionMessage = new Action();
        emailActionMessage.setBundle(StringUtils.EMPTY);
        emailActionMessage.setApplication(StringUtils.EMPTY);
        emailActionMessage.setTimestamp(LocalDateTime.of(2022, 10, 3, 15, 22, 13, 25));
        emailActionMessage.setEventType(eventType);
        emailActionMessage.setRecipients(List.of());

        emailActionMessage.setContext(
            new Context.ContextBuilder()
                .withAdditionalProperty("system_check_in", "2022-08-03T15:22:42.199046")
                .withAdditionalProperty("ImageName", "Test name")
                .build()
        );

        emailActionMessage.setEvents(List.of(
            new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(
                    new Payload.PayloadBuilder()
                        .withAdditionalProperty("ImageSetID", "1234")
                        .withAdditionalProperty("ImageId", "5678")
                        .withAdditionalProperty("ID", "DEVICE-9012")
                        .build()
                )
                .build()
        ));

        emailActionMessage.setAccountId(StringUtils.EMPTY);
        emailActionMessage.setOrgId(DEFAULT_ORG_ID);

        return emailActionMessage;
    }

    public static void writeEmailTemplate(String result, String fileName) {
        final String TARGET_DIR = "target/";
        try {
            Files.createDirectories(Paths.get(TARGET_DIR + fileName.split("/")[0]));
            Files.write(Paths.get(TARGET_DIR + fileName), result.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.out.println("An error occurred");
            e.printStackTrace();
        }
    }
}
