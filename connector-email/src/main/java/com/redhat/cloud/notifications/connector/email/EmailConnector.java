package com.redhat.cloud.notifications.connector.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.connector.ConnectorProcessor;
import com.redhat.cloud.notifications.connector.ExceptionProcessor;
import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import com.redhat.cloud.notifications.connector.email.model.EmailNotification;
import com.redhat.cloud.notifications.connector.email.model.settings.User;
import com.redhat.cloud.notifications.connector.email.processors.bop.BOPManager;
import com.redhat.cloud.notifications.connector.email.processors.recipients.RecipientsQuery;
import com.redhat.cloud.notifications.connector.email.processors.recipientsresolver.RecipientsResolverService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Email connector implementation using the new Quarkus-based architecture.
 * This replaces the Camel-based email routing functionality.
 */
@ApplicationScoped
public class EmailConnector extends ConnectorProcessor {

    private static final String EMAIL_RESPONSE_TIME_METRIC = "email.response.time";

    @Inject
    EmailConnectorConfig emailConnectorConfig;

    @Inject
    @RestClient
    RecipientsResolverService recipientsResolverService;

    @Inject
    BOPManager bopManager;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    MeterRegistry meterRegistry;

    @Override
    protected Uni<ConnectorResult> processCloudEvent(ExceptionProcessor.ProcessingContext context) {
        return Uni.createFrom().item(() -> {
            Timer.Sample timer = Timer.start(meterRegistry);

            try {
                JsonObject cloudEvent = context.getOriginalCloudEvent();
                EmailNotification emailNotification = objectMapper.readValue(
                        cloudEvent.encode(),
                        EmailNotification.class
                );

                Set<User> recipients = resolveRecipients(emailNotification);
                sendEmails(emailNotification, recipients);

                timer.stop(meterRegistry.timer(EMAIL_RESPONSE_TIME_METRIC));

                Log.infof("Delivered email event %s (orgId %s) to %d recipients",
                        context.getId(),
                        context.getOrgId(),
                        recipients.size());

                return new ConnectorResult(
                        true,
                        String.format("Email event %s processed successfully for %d recipients",
                                context.getId(), recipients.size()),
                        context.getId(),
                        context.getOrgId(),
                        context.getOriginalCloudEvent()
                );

            } catch (Exception e) {
                timer.stop(meterRegistry.timer(EMAIL_RESPONSE_TIME_METRIC));
                Log.errorf(e, "Failed to process email event %s", context.getId());
                throw new RuntimeException("Failed to process email event: " + e.getMessage(), e);
            }
        });
    }

    private Set<User> resolveRecipients(EmailNotification emailNotification) {
        try {
            RecipientsQuery query = new RecipientsQuery();
            query.orgId = emailNotification.orgId();
            query.recipientSettings = new HashSet<>(emailNotification.recipientSettings());
            query.subscribers = new HashSet<>(emailNotification.subscribers());
            query.unsubscribers = new HashSet<>(emailNotification.unsubscribers());
            query.subscribedByDefault = emailNotification.subscribedByDefault();
            query.recipientsAuthorizationCriterion = emailNotification.recipientsAuthorizationCriterion();

            return recipientsResolverService.getRecipients(query);
        } catch (Exception e) {
            Log.errorf(e, "Failed to resolve recipients for orgId %s", emailNotification.orgId());
            throw new RuntimeException("Failed to resolve recipients", e);
        }
    }

    private void sendEmails(EmailNotification emailNotification, Set<User> recipients) {
        try {
            // Filter out recipients with no email address
            List<String> emailAddresses = recipients.stream()
                    .map(User::getEmail)
                    .filter(email -> email != null && !email.trim().isEmpty())
                    .collect(Collectors.toList());

            if (emailAddresses.isEmpty()) {
                Log.warnf("No valid email addresses found for orgId %s", emailNotification.orgId());
                return;
            }

            // Check if emails should be internal only
            if (emailConnectorConfig.isEmailsInternalOnlyEnabled()) {
                emailAddresses = emailAddresses.stream()
                        .filter(email -> email.endsWith("@redhat.com"))
                        .collect(Collectors.toList());

                if (emailAddresses.isEmpty()) {
                    Log.infof("All email addresses filtered out due to internal-only mode for orgId %s",
                            emailNotification.orgId());
                    return;
                }
            }

            // Split recipients if there are too many per email
            int maxRecipientsPerEmail = emailConnectorConfig.getMaxRecipientsPerEmail();
            if (emailAddresses.size() > maxRecipientsPerEmail) {
                // Send in batches
                for (int i = 0; i < emailAddresses.size(); i += maxRecipientsPerEmail) {
                    int endIndex = Math.min(i + maxRecipientsPerEmail, emailAddresses.size());
                    List<String> batch = emailAddresses.subList(i, endIndex);
                    sendEmailBatch(emailNotification, batch);
                }
            } else {
                sendEmailBatch(emailNotification, emailAddresses);
            }

        } catch (Exception e) {
            Log.errorf(e, "Failed to send emails for orgId %s", emailNotification.orgId());
            throw new RuntimeException("Failed to send emails", e);
        }
    }

    private void sendEmailBatch(EmailNotification emailNotification, List<String> recipients) {
        bopManager.sendToBop(
                recipients,
                emailNotification.emailSubject(),
                emailNotification.emailBody(),
                emailNotification.emailSender()
        );

        Log.infof("Sent email batch to %d recipients for orgId %s",
                recipients.size(), emailNotification.orgId());
    }
}
