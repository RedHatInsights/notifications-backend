package com.redhat.cloud.notifications.processors.email.aggregators;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.db.EmailAggregationResources;
import com.redhat.cloud.notifications.db.EndpointEmailSubscriptionResources;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.ScheduledExecution;
import io.vertx.core.Vertx;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static java.time.ZoneOffset.UTC;

@ApplicationScoped
class DailyEmailAggregationJob {

    private static final Logger LOG = Logger.getLogger(DailyEmailAggregationJob.class.getName());

    @Inject
    EmailAggregationResources emailAggregationResources;

    @Inject
    EndpointEmailSubscriptionResources subscriptionResources;

    @ConfigProperty(name = "quarkus.aggregator.serializer")
    String serializer;

    @ConfigProperty(name = "quarkus.aggregator.broker-url")
    String brokerUrl;

    @ConfigProperty(name = "quarkus.aggregator.acks")
    String acks;

    @ConfigProperty(name = "quarkus.aggregator.topic.name")
    String topicName;

//    @Scheduled(identity = "dailyEmailProcessor", cron = "{email.subscription.daily.cron}")
    @Scheduled(identity = "dailyEmailProcessor", every = "1m")
    public void processDailyEmail(ScheduledExecution se) throws JsonProcessingException {
        List<EmailAggregation> aggregatedEmails = processAggregateEmails(se.getScheduledFireTime());

        Map<String, String> config = createKafkaConfig();
        KafkaProducer<String, String> producer = KafkaProducer.create(Vertx.vertx(), config);

        KafkaProducerRecord<String, String> records = KafkaProducerRecord.create(topicName, new ObjectMapper().writeValueAsString(aggregatedEmails));
        producer.write(records);
    }

    List<EmailAggregation> processAggregateEmails(Instant scheduledFireTime) {
        Instant yesterdayScheduledFireTime = scheduledFireTime.minus(EmailSubscriptionType.DAILY.getDuration());

        LocalDateTime endTime = LocalDateTime.ofInstant(scheduledFireTime, UTC);
        LocalDateTime startTime = LocalDateTime.ofInstant(yesterdayScheduledFireTime, UTC);
        final LocalDateTime aggregateStarted = LocalDateTime.now();

        LOG.info(String.format("Running %s email aggregation for period (%s, %s)", EmailSubscriptionType.DAILY, startTime, endTime));

        final List<EmailAggregationKey> applicationsWithPendingAggregation = emailAggregationResources.getApplicationsWithPendingAggregation(startTime, endTime);

        System.out.println("Aggregation Keys " + applicationsWithPendingAggregation);

        List<List<EmailAggregation>> list = new LinkedList<>();
        for (EmailAggregationKey key : applicationsWithPendingAggregation) {
            List<EmailAggregation> emailAggregations = processAggregateEmailsByAggregationKey(key, startTime, endTime, EmailSubscriptionType.DAILY);
            list.add(emailAggregations);
        }

        LOG.info(
                String.format(
                        "Finished running %s email aggregation for period (%s, %s) after %d seconds. %d (accountIds, applications) pairs were processed",
                        EmailSubscriptionType.DAILY,
                        startTime,
                        endTime,
                        ChronoUnit.SECONDS.between(aggregateStarted, LocalDateTime.now()),
                        list.size()
                )
        );
        if (list.size() == 0) {
            return new LinkedList<>();
        }
        return list.get(0);
    }

    private List<EmailAggregation> processAggregateEmailsByAggregationKey(EmailAggregationKey aggregationKey, LocalDateTime startTime, LocalDateTime endTime, EmailSubscriptionType emailSubscriptionType) {
        final Long subscriberCount = subscriptionResources.getEmailSubscribersCount(aggregationKey.getAccountId(), aggregationKey.getBundle(), aggregationKey.getApplication(), emailSubscriptionType);
        AbstractEmailPayloadAggregator aggregator = PoliciesEmailPayloadAggregator.by(aggregationKey);

        List<EmailAggregation> emailAggregations = new LinkedList<>();
        if (subscriberCount > 0 && aggregator != null) {
            emailAggregations = emailAggregationResources.getEmailAggregation(aggregationKey, startTime, endTime);
            for (EmailAggregation emailAggregation : emailAggregations) {
                aggregator.aggregate(emailAggregation);
            }
        }

        // Nothing to do, delete them right away.
        emailAggregationResources.purgeOldAggregation(aggregationKey, endTime);

        if (aggregator == null || aggregator.getProcessedAggregations() == 0) {
            return null;
        }

        return emailAggregations;
    }

    private Map<String, String> createKafkaConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("bootstrap.servers", brokerUrl);
        config.put("key.serializer", serializer);
        config.put("value.serializer", serializer);
        config.put("acks", acks);
        return config;
    }
}
