package com.redhat.cloud.notifications;

/**
 *
 */
public interface Constants {
    String API_INTEGRATIONS_V_1_0 = "/api/integrations/v1.0";
    String API_INTEGRATIONS_V_2_0 = "/api/integrations/v2.0";
    String API_INTEGRATIONS_V_3_0 = "/api/integrations/v3.0";

    String API_NOTIFICATIONS_V_1_0 = "/api/notifications/v1.0";
    String API_NOTIFICATIONS_V_2_0 = "/api/notifications/v2.0";
    String API_NOTIFICATIONS_V_3_0 = "/api/notifications/v3.0";

    /**
     * Legacy base path of the internal APIs. It is kept while clients migrate to the
     * {@code /internal/notifications/v1/{component}} paths below, and will be removed afterwards.
     */
    String API_INTERNAL = "/internal";

    /**
     * Internal API base paths following the {@code /internal/{service}/{version}} convention (RHCLOUD-52372).
     * The service is always {@code notifications}, the component is the module name without the
     * {@code notifications-} prefix.
     */
    String API_INTERNAL_NOTIFICATIONS_V_1 = "/internal/notifications/v1";
    String API_INTERNAL_NOTIFICATIONS_BACKEND = API_INTERNAL_NOTIFICATIONS_V_1 + "/backend";
    String API_INTERNAL_NOTIFICATIONS_ENGINE = API_INTERNAL_NOTIFICATIONS_V_1 + "/engine";
    String X_RH_EXPORT_SERVICE_PSK = "x-rh-exports-psk";
    String X_RH_IDENTITY_HEADER = "x-rh-identity";
    String X_RH_SOURCES_ORG_ID = "x-rh-sources-org-id";
    String X_RH_SOURCES_PSK = "x-rh-sources-psk";
}
