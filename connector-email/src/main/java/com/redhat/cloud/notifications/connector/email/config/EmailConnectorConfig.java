package com.redhat.cloud.notifications.connector.email.config;

import com.redhat.cloud.notifications.connector.ConnectorConfig;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class EmailConnectorConfig extends ConnectorConfig {
    private static final String BOP_API_TOKEN = "notifications.connector.user-provider.bop.api_token";
    private static final String BOP_CLIENT_ID = "notifications.connector.user-provider.bop.client_id";
    private static final String BOP_ENV = "notifications.connector.user-provider.bop.env";
    private static final String BOP_URL = "notifications.connector.user-provider.bop.url";
    private static final String FETCH_USERS_RBAC_ENABLED = "notifications.connector.fetch.users.rbac.enabled";
    private static final String IT_ELEMENTS_PAGE = "notifications.connector.user-provider.it.elements-per-page";
    private static final String IT_KEYSTORE_LOCATION = "notifications.connector.user-provider.it.key-store-location";
    private static final String IT_KEYSTORE_PASSWORD = "notifications.connector.user-provider.it.key-store-password";
    private static final String IT_USER_SERVICE_URL = "notifications.connector.user-provider.it.url";
    private static final String RBAC_ELEMENTS_PAGE = "notifications.connector.user-provider.rbac.elements-per-page";
    private static final String RBAC_URL = "notifications.connector.user-provider.rbac.url";
    @Deprecated(forRemoval = true)
    private static final String SINGLE_EMAIL_PER_USER = "notifications.connector.single-email-per-user.enabled";

    @ConfigProperty(name = BOP_API_TOKEN)
    String bopApiToken;

    @ConfigProperty(name = BOP_CLIENT_ID)
    String bopClientId;

    @ConfigProperty(name = BOP_ENV)
    String bopEnv;

    @ConfigProperty(name = BOP_URL)
    String bopURL;

    @ConfigProperty(name = SINGLE_EMAIL_PER_USER, defaultValue = "true")
    boolean singleEmailPerUserEnabled;

    @ConfigProperty(name = FETCH_USERS_RBAC_ENABLED, defaultValue = "true")
    boolean fetchUsersWithRBAC;

    @ConfigProperty(name = IT_ELEMENTS_PAGE, defaultValue = "1000")
    Integer itElementsPerPage;

    @ConfigProperty(name = IT_KEYSTORE_LOCATION, defaultValue = "/mnt/secrets/clientkeystore.jks")
    String itKeyStoreLocation;

    @ConfigProperty(name = IT_KEYSTORE_PASSWORD)
    String itKeyStorePassword;

    @ConfigProperty(name = IT_USER_SERVICE_URL)
    String itUserServiceURL;

    @ConfigProperty(name = RBAC_ELEMENTS_PAGE, defaultValue = "1000")
    Integer rbacElementsPerPage;

    @ConfigProperty(name = RBAC_URL)
    String rbacURL;

    @Override
    public void log() {
        final Map<String, Object> additionalEntries = new HashMap<>();

        additionalEntries.put(BOP_API_TOKEN, this.bopApiToken);
        additionalEntries.put(BOP_CLIENT_ID, this.bopClientId);
        additionalEntries.put(BOP_ENV, this.bopEnv);
        additionalEntries.put(BOP_URL, this.bopURL);
        additionalEntries.put(FETCH_USERS_RBAC_ENABLED, this.fetchUsersWithRBAC);
        additionalEntries.put(IT_ELEMENTS_PAGE, this.itElementsPerPage);
        additionalEntries.put(IT_KEYSTORE_LOCATION, this.itKeyStoreLocation);
        additionalEntries.put(IT_KEYSTORE_PASSWORD, this.itKeyStorePassword);
        additionalEntries.put(IT_USER_SERVICE_URL, this.itUserServiceURL);
        additionalEntries.put(RBAC_ELEMENTS_PAGE, this.rbacElementsPerPage);
        additionalEntries.put(RBAC_URL, this.rbacURL);
        additionalEntries.put(SINGLE_EMAIL_PER_USER, this.singleEmailPerUserEnabled);

        log(additionalEntries);
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

    public boolean isFetchUsersWithRBAC() {
        return this.fetchUsersWithRBAC;
    }

    public int getItElementsPerPage() {
        return this.itElementsPerPage;
    }

    public String getItKeyStoreLocation() {
        return this.itKeyStoreLocation;
    }

    public String getItKeyStorePassword() {
        return this.itKeyStorePassword;
    }

    public String getItUserServiceURL() {
        return this.itUserServiceURL;
    }

    public Integer getRbacElementsPerPage() {
        return this.rbacElementsPerPage;
    }

    public String getRbacURL() {
        return this.rbacURL;
    }

    public boolean isSingleEmailPerUserEnabled() {
        return this.singleEmailPerUserEnabled;
    }
}