package com.redhat.cloud.notifications.connector.email.config;

import com.redhat.cloud.notifications.connector.http.HttpConnectorConfig;
import io.getunleash.UnleashContext;
import io.quarkus.runtime.configuration.ProfileManager;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;

import static io.quarkus.runtime.LaunchMode.TEST;

@ApplicationScoped
public class EmailConnectorConfig extends HttpConnectorConfig {
    private static final String BOP_API_TOKEN = "notifications.connector.user-provider.bop.api_token";
    private static final String BOP_CLIENT_ID = "notifications.connector.user-provider.bop.client_id";
    private static final String BOP_ENV = "notifications.connector.user-provider.bop.env";
    private static final String BOP_URL = "notifications.connector.user-provider.bop.url";
    private static final String KAFKA_INCOMING_HIGH_VOLUME_MAX_POLL_INTERVAL_MS = "notifications.connector.kafka.incoming.high-volume.max-poll-interval-ms";
    private static final String KAFKA_INCOMING_HIGH_VOLUME_MAX_POLL_RECORDS = "notifications.connector.kafka.incoming.high-volume.max-poll-records";
    private static final String KAFKA_INCOMING_HIGH_VOLUME_POLL_ON_ERROR = "notifications.connector.kafka.incoming.high-volume.poll-on-error";
    private static final String KAFKA_INCOMING_HIGH_VOLUME_TOPIC = "notifications.connector.kafka.incoming.high-volume.topic";
    private static final String KAFKA_INCOMING_HIGH_VOLUME_TOPIC_ENABLED = "notifications.connector.kafka.incoming.high-volume.topic.enabled";
    private static final String MAX_RECIPIENTS_PER_EMAIL = "notifications.connector.max-recipients-per-email";
    private static final String RECIPIENTS_RESOLVER_USER_SERVICE_URL = "notifications.connector.recipients-resolver.url";
    private static final String NOTIFICATIONS_EMAILS_INTERNAL_ONLY_ENABLED = "notifications.emails-internal-only.enabled";

    @ConfigProperty(name = BOP_API_TOKEN)
    String bopApiToken;

    @ConfigProperty(name = BOP_CLIENT_ID)
    String bopClientId;

    @ConfigProperty(name = BOP_ENV)
    String bopEnv;

    @ConfigProperty(name = BOP_URL)
    String bopURL;

    // https://docs.confluent.io/platform/current/installation/configuration/consumer-configs.html#max-poll-interval-ms
    @ConfigProperty(name = KAFKA_INCOMING_HIGH_VOLUME_MAX_POLL_INTERVAL_MS, defaultValue = "300000")
    int incomingKafkaHighVolumeMaxPollIntervalMs;

    // https://docs.confluent.io/platform/current/installation/configuration/consumer-configs.html#max-poll-records
    @ConfigProperty(name = KAFKA_INCOMING_HIGH_VOLUME_MAX_POLL_RECORDS, defaultValue = "500")
    int incomingKafkaHighVolumeMaxPollRecords;

    @ConfigProperty(name = KAFKA_INCOMING_HIGH_VOLUME_POLL_ON_ERROR, defaultValue = "RECONNECT")
    String incomingKafkaHighVolumePollOnError;

    @ConfigProperty(name = KAFKA_INCOMING_HIGH_VOLUME_TOPIC)
    String incomingKafkaHighVolumeTopic;

    @ConfigProperty(name = KAFKA_INCOMING_HIGH_VOLUME_TOPIC_ENABLED, defaultValue = "false")
    Boolean incomingKafkaHighVolumeTopicEnabled;

    @ConfigProperty(name = RECIPIENTS_RESOLVER_USER_SERVICE_URL)
    String recipientsResolverServiceURL;

    @ConfigProperty(name = MAX_RECIPIENTS_PER_EMAIL, defaultValue = "50")
    int maxRecipientsPerEmail;

    @ConfigProperty(name = NOTIFICATIONS_EMAILS_INTERNAL_ONLY_ENABLED, defaultValue = "false")
    boolean emailsInternalOnlyEnabled;

    private String enableBopEmailServiceWithSslChecks;

    @PostConstruct
    void emailConnectorPostConstruct() {
        enableBopEmailServiceWithSslChecks = toggleRegistry.register("enable-bop-service-ssl-checks", true);
    }

    @Override
    protected Map<String, Object> getLoggedConfiguration() {
        Map<String, Object> config = super.getLoggedConfiguration();

        /*
         * /!\ WARNING /!\
         * DO NOT log config values that come from OpenShift secrets.
         */

        config.put(BOP_ENV, bopEnv);
        config.put(BOP_URL, bopURL);
        config.put(KAFKA_INCOMING_HIGH_VOLUME_MAX_POLL_INTERVAL_MS, incomingKafkaHighVolumeMaxPollIntervalMs);
        config.put(KAFKA_INCOMING_HIGH_VOLUME_MAX_POLL_RECORDS, incomingKafkaHighVolumeMaxPollRecords);
        config.put(KAFKA_INCOMING_HIGH_VOLUME_POLL_ON_ERROR, incomingKafkaHighVolumePollOnError);
        config.put(KAFKA_INCOMING_HIGH_VOLUME_TOPIC, incomingKafkaHighVolumeTopic);
        config.put(KAFKA_INCOMING_HIGH_VOLUME_TOPIC_ENABLED, incomingKafkaHighVolumeTopicEnabled);
        config.put(RECIPIENTS_RESOLVER_USER_SERVICE_URL, recipientsResolverServiceURL);
        config.put(MAX_RECIPIENTS_PER_EMAIL, maxRecipientsPerEmail);
        config.put(NOTIFICATIONS_EMAILS_INTERNAL_ONLY_ENABLED, emailsInternalOnlyEnabled);
        config.put(enableBopEmailServiceWithSslChecks + " for all orgIds", isEnableBopServiceWithSslChecks(null));

        /*
         * /!\ WARNING /!\
         * DO NOT log config values that come from OpenShift secrets.
         */

        return config;
    }

    public String getBopApiToken() {
        return this.bopApiToken;
    }

    public String getBopClientId() {
        return this.bopClientId;
    }

    public String getBopEnv() {
        return this.bopEnv;
    }

    public String getBopURL() {
        return this.bopURL;
    }

    public String getRecipientsResolverServiceURL() {
        return recipientsResolverServiceURL;
    }

    public int getIncomingKafkaHighVolumeMaxPollIntervalMs() {
        return this.incomingKafkaHighVolumeMaxPollIntervalMs;
    }

    public int getIncomingKafkaHighVolumeMaxPollRecords() {
        return this.incomingKafkaHighVolumeMaxPollRecords;
    }

    public String getIncomingKafkaHighVolumePollOnError() {
        return this.incomingKafkaHighVolumePollOnError;
    }

    public String getIncomingKafkaHighVolumeTopic() {
        return this.incomingKafkaHighVolumeTopic;
    }

    public boolean isIncomingKafkaHighVolumeTopicEnabled() {
        return this.incomingKafkaHighVolumeTopicEnabled;
    }

    public int getMaxRecipientsPerEmail() {
        return maxRecipientsPerEmail;
    }

    public boolean isEmailsInternalOnlyEnabled() {
        return emailsInternalOnlyEnabled;
    }

    public void setEmailsInternalOnlyEnabled(boolean emailsInternalOnlyEnabled) {
        checkTestLaunchMode();
        this.emailsInternalOnlyEnabled = emailsInternalOnlyEnabled;
    }

    public boolean isEnableBopServiceWithSslChecks(String orgId) {
        UnleashContext unleashContext = UnleashContext.builder()
            .addProperty("orgId", orgId)
            .build();
        return unleash.isEnabled(enableBopEmailServiceWithSslChecks, unleashContext, false);
    }

    /**
     * This method throws an {@link IllegalStateException} if it is invoked with a launch mode different from
     * {@link io.quarkus.runtime.LaunchMode#TEST TEST}. It should be added to methods that allow overriding a
     * config value from tests only, preventing doing so from runtime code.
     */
    private static void checkTestLaunchMode() {
        if (ProfileManager.getLaunchMode() != TEST) {
            throw new IllegalStateException("Illegal config value override detected");
        }
    }
}
