package com.redhat.cloud.notifications.clowder;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import static jakarta.interceptor.Interceptor.Priority.PLATFORM_BEFORE;

/*
 * This bean is required to make sure that SmallRye Reactive Messaging will use the configuration from
 * clowder-quarkus-config-source during the Kafka SASL authentication process.
 */
@ApplicationScoped
public class KafkaSaslInitializer {

    private static final String KAFKA_SASL_JAAS_CONFIG = "kafka.sasl.jaas.config";
    private static final String KAFKA_SASL_MECHANISM = "kafka.sasl.mechanism";
    private static final String KAFKA_SECURITY_PROTOCOL = "kafka.security.protocol";
    private static final String KAFKA_SSL_TRUSTSTORE_LOCATION = "kafka.ssl.truststore.location";
    private static final String KAFKA_SSL_TRUSTSTORE_TYPE = "kafka.ssl.truststore.type";
    private static final String SASL_SSL = "SASL_SSL";
    private static final String SSL = "SSL";

    void init(@Observes @Priority(PLATFORM_BEFORE) StartupEvent event) {
        Config config = ConfigProvider.getConfig();
        config.getOptionalValue(KAFKA_SECURITY_PROTOCOL, String.class).ifPresent(securityProtocol -> {
            setValue(KAFKA_SECURITY_PROTOCOL, securityProtocol);
            switch (securityProtocol) {
                case SSL -> {
                    Log.info("Initializing Kafka SSL configuration...");
                    configureTruststoreIfPresent(config);
                }
                case SASL_SSL -> {
                    Log.info("Initializing Kafka SASL_SSL configuration...");
                    configureSasl(config);
                    configureTruststoreIfPresent(config);
                }
                default -> throw new IllegalStateException("Unexpected Kafka security protocol: " + securityProtocol);
            }
        });
    }

    private static void configureSasl(Config config) {
        String saslMechanism = config.getValue(KAFKA_SASL_MECHANISM, String.class);
        String saslJaasConfig = config.getValue(KAFKA_SASL_JAAS_CONFIG, String.class);
        setValue(KAFKA_SASL_MECHANISM, saslMechanism); // PLAIN or SCRAM-SHA-512
        setValue(KAFKA_SASL_JAAS_CONFIG, saslJaasConfig);
    }

    private static void configureTruststoreIfPresent(Config config) {
        config.getOptionalValue(KAFKA_SSL_TRUSTSTORE_LOCATION, String.class).ifPresent(truststoreLocation -> {
            String truststoreType = config.getValue(KAFKA_SSL_TRUSTSTORE_TYPE, String.class);
            setValue(KAFKA_SSL_TRUSTSTORE_LOCATION, truststoreLocation);
            setValue(KAFKA_SSL_TRUSTSTORE_TYPE, truststoreType);
        });
    }

    private static void setValue(String configKey, String configValue) {
        System.setProperty(configKey, configValue);
        Log.infof("%s has been set", configKey);
    }
}
