package com.redhat.cloud.notifications.auth.principal.rhid;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RhServiceAccountIdentity extends RhIdentity {

    @JsonProperty("org_id")
    private String orgId;

    @JsonProperty("service_account")
    private ServiceAccount serviceAccount;

    public ServiceAccount getServiceAccount() {
        return serviceAccount;
    }

    @Override
    public String getOrgId() {
        return orgId;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ServiceAccount {
        @JsonProperty("username")
        private String username;

        @JsonProperty("client_id")
        private String clientId;

        public String getClientId() {
            return clientId;
        }
    }

    @Override
    public String getName() {
        return getServiceAccount().getClientId();
    }
}
