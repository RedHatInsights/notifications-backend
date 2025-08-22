package helpers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.ingress.Recipient;
import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.time.ZoneOffset.UTC;

@ApplicationScoped
public class TestHelpers {

    @Inject
    TemplateService templateService;

    @Inject
    ObjectMapper objectMapper;

    public static final String expectedTestEnvUrlValue = "https://localhost";

    public static final String HCC_LOGO_TARGET = "Logo-Red_Hat-Hybrid_Cloud_Console-A-Reverse-RGB.png";
    public static final String DEFAULT_ORG_ID = "default-org-id";
    public static final String POLICY_ID_1 = "abcd-efghi-jkl-lmn";
    public static final String POLICY_NAME_1 = "Foobar";
    public static final String POLICY_ID_2 = "0123-456-789-5721f";
    public static final String POLICY_NAME_2 = "Latest foo is installed";
    public static final String EVENT_TYPE = "policy-triggered";

    public static Action createPoliciesAction(String accountId, String bundle, String application, String hostDisplayName) {
        Action emailActionMessage = new Action();
        emailActionMessage.setBundle(bundle);
        emailActionMessage.setApplication(application);
        emailActionMessage.setTimestamp(LocalDateTime.of(2020, 10, 3, 15, 22, 13, 25));
        emailActionMessage.setEventType(EVENT_TYPE);
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
                                        .withAdditionalProperty("policy_id", POLICY_ID_1)
                                        .withAdditionalProperty("policy_name", POLICY_NAME_1)
                                        .withAdditionalProperty("policy_description", "not-used-desc")
                                        .withAdditionalProperty("policy_condition", "not-used-condition")
                                        .build()
                        )
                        .build(),
                new Event.EventBuilder()
                        .withMetadata(new Metadata.MetadataBuilder().build())
                        .withPayload(
                                new Payload.PayloadBuilder()
                                        .withAdditionalProperty("policy_id", POLICY_ID_2)
                                        .withAdditionalProperty("policy_name", POLICY_NAME_2)
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
        emailActionMessage.setEventType(EVENT_TYPE);
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

    public static Action createAnsibleAction(String eventType, String envName, String bitwardenURL) {
        Action emailActionMessage = new Action();
        emailActionMessage.setBundle(StringUtils.EMPTY);
        emailActionMessage.setApplication(StringUtils.EMPTY);
        emailActionMessage.setTimestamp(LocalDateTime.of(2020, 10, 3, 15, 22, 13, 25));
        emailActionMessage.setEventType(eventType);
        emailActionMessage.setRecipients(List.of());

        emailActionMessage.setContext(
            new Context.ContextBuilder()
                .withAdditionalProperty("env_name", envName)
                .withAdditionalProperty("bitwarden_url", bitwardenURL)
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
        emailActionMessage.setEventType(EVENT_TYPE);
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

    public static Action createVulnerabilityAction() {
        Action emailActionMessage = new Action();
        emailActionMessage.setBundle(StringUtils.EMPTY);
        emailActionMessage.setApplication(StringUtils.EMPTY);
        emailActionMessage.setTimestamp(LocalDateTime.of(2020, 10, 3, 15, 22, 13, 25));
        emailActionMessage.setEventType(EVENT_TYPE);
        emailActionMessage.setRecipients(List.of());

        emailActionMessage.setContext(
            new Context.ContextBuilder()
                .withAdditionalProperty("vulnerability", Map.of("reported_cves", List.of("CVE1", "CVE2", "CVE3")))
                .build()
        );

        emailActionMessage.setEvents(List.of(
            new Event.EventBuilder().withMetadata(new Metadata.MetadataBuilder()
                    .build())
                .withPayload(new Payload.PayloadBuilder()
                    .withAdditionalProperty("reported_cve", "CVE-TEST")
                    .build())
                .build()
        ));

        emailActionMessage.setAccountId(StringUtils.EMPTY);
        emailActionMessage.setOrgId(DEFAULT_ORG_ID);

        return emailActionMessage;
    }

    public static Action createIntegrationsFailedAction() {
        Action emailActionMessage = new Action();
        emailActionMessage.setBundle(StringUtils.EMPTY);
        emailActionMessage.setApplication(StringUtils.EMPTY);
        emailActionMessage.setTimestamp(LocalDateTime.of(2020, 10, 3, 15, 22, 13, 25));
        emailActionMessage.setEventType(EVENT_TYPE);
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
        emailActionMessage.setEventType(EVENT_TYPE);
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
        emailActionMessage.setEventType(EVENT_TYPE);
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
        emailActionMessage.setEventType(EVENT_TYPE);
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

    public static Action createImageBuilderAction(String eventType) {
        Action emailActionMessage = new Action();
        emailActionMessage.setBundle("rhel");
        emailActionMessage.setApplication("image-builder");
        emailActionMessage.setTimestamp(LocalDateTime.now());
        emailActionMessage.setEventType(eventType);
        emailActionMessage.setAccountId(StringUtils.EMPTY);
        emailActionMessage.setOrgId(DEFAULT_ORG_ID);
        emailActionMessage.setRecipients(List.of());

        emailActionMessage.setContext(
            new Context.ContextBuilder()
            .withAdditionalProperty("provider", "aws")
            .withAdditionalProperty("launch_id", 3011)
            .build());
        if (eventType.equals("launch-success")) {
            emailActionMessage.setEvents(List.of(
                new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(
                    new Payload.PayloadBuilder()
                    .withAdditionalProperty("instance_id", "i-0cbaed564af9faf")
                    .withAdditionalProperty("detail",
                        Map.of("public_ipv4", "92.123.32.3", "public_dns",
                            "ec2-92-123-32-3.compute-1.amazonaws.com"))
                    .build())
                .build(),
                new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(
                    new Payload.PayloadBuilder()
                    .withAdditionalProperty("instance_id", "i-0aba12564af9faf")
                    .withAdditionalProperty("detail",
                        Map.of("public_ipv4", "91.123.32.4", "public_dns",
                            ""))
                    .build())
                .build()));
        } else {
            emailActionMessage.setEvents(List.of(
                new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(
                    new Payload.PayloadBuilder()
                    .withAdditionalProperty("error", "Some launch error")
                    .build())
                .build()));

        }
        return emailActionMessage;
    }

    public static Action createIntegrationDisabledAction(String errorType, String integrationName, Integer statusCode) {
        Context.ContextBuilderBase contextBuilder = new Context.ContextBuilder()
            .withAdditionalProperty("error_type", errorType)
            .withAdditionalProperty("endpoint_name", integrationName);

        if (statusCode != null) {
            contextBuilder.withAdditionalProperty("status_code", statusCode);
        }

        Event event = new Event.EventBuilder()
            .withPayload(new Payload.PayloadBuilder().build())
            .build();

        Recipient recipients = new Recipient.RecipientBuilder()
            .withOnlyAdmins(true)
            .withIgnoreUserPreferences(true)
            .build();

        return new Action.ActionBuilder()
            .withId(UUID.randomUUID())
            .withTimestamp(LocalDateTime.now(UTC))
            .withContext(contextBuilder.build())
            .withEvents(List.of(event))
            .withRecipients(List.of(recipients))
            .build();
    }

    public static void writeEmailTemplate(String result, String fileName) {
        final String TARGET_DIR = "target";
        try {
            String[] splitPath = fileName.split("/");
            String actualPath = TARGET_DIR;
            for (int part = 0; part < splitPath.length - 1; part++) {
                actualPath += "/" + splitPath[part];
            }
            Files.createDirectories(Paths.get(actualPath));
            Files.write(Paths.get(TARGET_DIR + "/" + fileName), result.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.out.println("An error occurred");
            e.printStackTrace();
        }
    }

    public static Map<String, Map<String, String>> buildSourceParameter(String bundleDisplayName, String applicationDisplayName, String eventTypeDisplayName) {
        return Map.of("bundle", Map.of("display_name", bundleDisplayName),
            "application", Map.of("display_name", applicationDisplayName),
            "event_type", Map.of("display_name", eventTypeDisplayName));
    }

    public String renderTemplate(final IntegrationType integrationType, final String eventType, final Action action, final String inventoryUrl, final String applicationUrl) {
        TemplateDefinition templateConfig = new TemplateDefinition(integrationType, "rhel", "unknown-app", eventType);
        Map<String, Object> map = objectMapper
            .convertValue(action, new TypeReference<Map<String, Object>>() { });
        map.put("inventory_url", inventoryUrl);
        map.put("application_url", applicationUrl);
        map.put("source", TestHelpers.buildSourceParameter("Red Hat Enterprise Linux", "Policies", "not use"));
        return templateService.renderTemplate(templateConfig, map);
    }
}
