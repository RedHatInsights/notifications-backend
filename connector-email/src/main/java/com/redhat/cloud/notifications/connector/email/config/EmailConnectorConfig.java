package com.redhat.cloud.notifications.connector.email.config;

import com.redhat.cloud.notifications.connector.http.HttpConnectorConfig;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;

@ApplicationScoped
/*
 * @Alternative and Priority will soon go away.
 * See https://github.com/quarkusio/quarkus/issues/37042 for more details about the replacement.
 */
@Alternative
@Priority(0) // The value doesn't matter.
public class EmailConnectorConfig extends HttpConnectorConfig {
    private static final String BOP_API_TOKEN = "notifications.connector.user-provider.bop.api_token";
    private static final String BOP_CLIENT_ID = "notifications.connector.user-provider.bop.client_id";
    private static final String BOP_ENV = "notifications.connector.user-provider.bop.env";
    private static final String BOP_URL = "notifications.connector.user-provider.bop.url";

    private static final String RECIPIENTS_RESOLVER_USER_SERVICE_URL = "notifications.connector.recipients-resolver.url";

    @ConfigProperty(name = BOP_API_TOKEN)
    String bopApiToken;

    @ConfigProperty(name = BOP_CLIENT_ID)
    String bopClientId;

    @ConfigProperty(name = BOP_ENV)
    String bopEnv;

    @ConfigProperty(name = BOP_URL)
    String bopURL;

    @ConfigProperty(name = RECIPIENTS_RESOLVER_USER_SERVICE_URL)
    String recipientsResolverServiceURL;

    @Override
    protected Map<String, Object> getLoggedConfiguration() {
        Map<String, Object> config = super.getLoggedConfiguration();

        /*
         * /!\ WARNING /!\
         * DO NOT log config values that come from OpenShift secrets.
         */

        config.put(BOP_ENV, this.bopEnv);
        config.put(BOP_URL, this.bopURL);
        config.put(RECIPIENTS_RESOLVER_USER_SERVICE_URL, recipientsResolverServiceURL);

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
}
