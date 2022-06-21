package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.CronJobRun;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import org.hibernate.Session;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;

import static java.util.stream.Collectors.toList;

@ApplicationScoped
public class EmailAggregationResources {

    @Inject
    Session session;

    public List<EmailAggregationKey> getApplicationsWithPendingAggregation(LocalDateTime start, LocalDateTime end) {
        String query = "SELECT DISTINCT account_id, org_id, bundle, application FROM email_aggregation WHERE created > :start AND created <= :end";
        List<Object[]> records = session.createNativeQuery(query)
                .setParameter("start", start)
                .setParameter("end", end)
                .getResultList();
        return records.stream()
                .map(record -> new EmailAggregationKey((String) record[0], (String) record[1], (String) record[2], (String) record[3]))
                .collect(toList());
    }

    public CronJobRun getLastCronJobRun() {
        String query = "FROM CronJobRun";
        return session.createQuery(query, CronJobRun.class).getSingleResult();
    }

    @Transactional
    public void updateLastCronJobRun(LocalDateTime lastRun) {
        String query = "UPDATE CronJobRun SET lastRun = :lastRun";
        session.createQuery(query)
                .setParameter("lastRun", lastRun)
                .executeUpdate();
    }
}
