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

import java.time.LocalDateTime;
import java.util.List;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;

public class TestHelpers {

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
}
