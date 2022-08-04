package com.redhat.cloud.notifications.events.orgid;

import io.quarkus.cache.CacheResult;
import io.quarkus.logging.Log;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.List;
import java.util.Map;

// TODO NOTIF-744 Remove this as soon as all onboarded apps include the org_id field in their Kafka messages.
@ApplicationScoped
public class OrgIdTranslator {

    @ConfigProperty(name = "processor.email.bop_apitoken")
    String bopApiToken;

    @ConfigProperty(name = "processor.email.bop_client_id")
    String bopClientId;

    @ConfigProperty(name = "processor.email.bop_env")
    String bopEnv;

    @Inject
    @RestClient
    Bop bop;

    @CacheResult(cacheName = "account-id-to-org-id")
    public String translate(String accountId) {
        Log.debugf("Calling BOP to translate EAN %s to an org ID", accountId);
        Map<String, String> result = bop.translateAccountIdsToOrgIds(bopApiToken, bopClientId, bopEnv, List.of(accountId));
        if (result != null && !result.isEmpty()) {
            String orgId = result.get(accountId);
            Log.debugf("EAN %s translated to org ID %s", accountId, orgId);
            return orgId;
        } else {
            Log.debugf("BOP did not know EAN %s", accountId);
            return null;
        }
    }
}
