package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import org.hibernate.Session;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import java.math.BigInteger;

@ApplicationScoped
public class EndpointEmailSubscriptionResources {

    @Inject
    Session session;

    @Transactional
    public Long getEmailSubscribersCount(String accountNumber, String bundleName, String applicationName, EmailSubscriptionType subscriptionType) {
        String query = "SELECT COUNT(user_id) FROM endpoint_email_subscriptions es, applications a, bundles b WHERE es.account_id = :accountId " +
                "AND es.application_id = a.id AND a.bundle_id = b.id AND b.name = :bundleName AND a.name = :applicationName AND es.subscription_type = :subscriptionType";
        BigInteger result = (BigInteger) session.createNativeQuery(query)
                .setParameter("accountId", accountNumber)
                .setParameter("bundleName", bundleName)
                .setParameter("applicationName", applicationName)
                .setParameter("subscriptionType", subscriptionType.name())
                .getSingleResult();
        return result.longValue();
    }
}
