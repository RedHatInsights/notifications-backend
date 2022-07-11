package com.redhat.cloud.notifications.clowder;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;

import static javax.interceptor.Interceptor.Priority.PLATFORM_BEFORE;

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
    private static final String PLAIN = "PLAIN";
    private static final String SASL_SSL = "SASL_SSL";
    private static final String SCRAM_SHA_512 = "SCRAM-SHA-512";

    void init(@Observes @Priority(PLATFORM_BEFORE) StartupEvent event) {
        Config config = ConfigProvider.getConfig();
        config.getOptionalValue(KAFKA_SECURITY_PROTOCOL, String.class).ifPresent(securityProtocol -> {
            switch (securityProtocol) {
                case SASL_SSL:
                    Log.info("Initializing Kafka SASL configuration...");
                    String saslMechanism = config.getValue(KAFKA_SASL_MECHANISM, String.class);
                    String saslJaasConfig = config.getValue(KAFKA_SASL_JAAS_CONFIG, String.class);
                    switch (saslMechanism) {
                        case PLAIN:
                            configurePlainAuthentication(securityProtocol, saslMechanism, saslJaasConfig);
                            break;
                        case SCRAM_SHA_512:
                            String truststoreLocation = config.getValue(KAFKA_SSL_TRUSTSTORE_LOCATION, String.class);
                            String truststoreType = config.getValue(KAFKA_SSL_TRUSTSTORE_TYPE, String.class);
                            configureScramAuthentication(securityProtocol, saslMechanism, saslJaasConfig, truststoreLocation, truststoreType);
                            break;
                        default:
                            throw new IllegalStateException("Unexpected Kafka SASL mechanism: " + saslMechanism);
                    }
                    break;
                default:
                    throw new IllegalStateException("Unexpected Kafka security protocol: " + securityProtocol);
            }
        });
    }

    private static void configurePlainAuthentication(String securityProtocol, String saslMechanism, String saslJaasConfig) {
        setValue(KAFKA_SECURITY_PROTOCOL, securityProtocol);
        setValue(KAFKA_SASL_MECHANISM, saslMechanism);
        setValue(KAFKA_SASL_JAAS_CONFIG, saslJaasConfig);
    }

    private static void configureScramAuthentication(String securityProtocol, String saslMechanism, String saslJaasConfig, String truststoreLocation, String truststoreType) {
        setValue(KAFKA_SECURITY_PROTOCOL, securityProtocol);
        setValue(KAFKA_SASL_MECHANISM, saslMechanism);
        setValue(KAFKA_SASL_JAAS_CONFIG, saslJaasConfig);
        setValue(KAFKA_SSL_TRUSTSTORE_LOCATION, truststoreLocation);
        setValue(KAFKA_SSL_TRUSTSTORE_TYPE, truststoreType);
    }

    private static void setValue(String configKey, String configValue) {
        System.setProperty(configKey, configValue);
        Log.infof("%s has been set", configKey);
    }
}
