package com.redhat.cloud.notifications.events.orgid;

import io.quarkus.cache.CacheResult;
import io.quarkus.logging.Log;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

// TODO NOTIF-744 Remove this as soon as all onboarded apps include the org_id field in their Kafka messages.
@ApplicationScoped
public class OrgIdTranslator {

    @Inject
    @RestClient
    ITUserServiceV2 itUserServiceV2;

    @CacheResult(cacheName = "account-id-to-org-id")
    public String translate(String accountId) {
        Log.debugf("Calling BOP to translate EAN %s to an org ID", accountId);
        OrgIdRequestBy by = new OrgIdRequestBy();
        by.ebsAccountNumber = accountId;
        OrgIdRequest request = new OrgIdRequest();
        request.by = by;
        OrgIdResponse response = itUserServiceV2.getOrgId(request);
        if (response != null && response.id != null) {
            Log.debugf("EAN %s translated to org ID %s", accountId, response.id);
            return response.id;
        } else {
            Log.debugf("BOP did not know EAN %s", accountId);
            return null;
        }
    }
}
