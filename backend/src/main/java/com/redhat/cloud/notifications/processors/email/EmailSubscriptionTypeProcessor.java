package com.redhat.cloud.notifications.processors.email;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.db.EmailAggregationResources;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.AggregationCommand;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.templates.EmailTemplate;
import com.redhat.cloud.notifications.templates.EmailTemplateFactory;
import io.quarkus.qute.TemplateInstance;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.redhat.cloud.notifications.models.EmailSubscriptionType.*;

@ApplicationScoped
public class EmailSubscriptionTypeProcessor {

    public static final String AGGREGATION_CHANNEL = "aggregation";

    private final Logger log = Logger.getLogger(this.getClass().getName());

    @Inject
    EmailAggregationResources emailAggregationResources;

    @Inject
    EmailTemplateFactory emailTemplateFactory;

    @Inject
    EmailSender emailSender;

    @Inject
    EmailAggregator emailAggregator;

    @Inject
    ObjectMapper objectMapper;

    @Incoming(AGGREGATION_CHANNEL)
    public Uni<Void> consumeEmailAggregations(String aggregationCommandJson) {
        AggregationCommand aggregationCommand;
        try {
            aggregationCommand = objectMapper.readValue(aggregationCommandJson, AggregationCommand.class);
        } catch (JsonProcessingException e) {
            log.log(Level.SEVERE, "Kafka aggregation payload parsing failed", e);
            return Uni.createFrom().nullItem();
        }

        return processAggregateEmailsByAggregationKey(
                aggregationCommand.getAggregationKey(),
                aggregationCommand.getStart(),
                aggregationCommand.getEnd(),
                aggregationCommand.getSubscriptionType(),
                // Delete on daily
                aggregationCommand.getSubscriptionType().equals(DAILY)
        ).onItem().ignoreAsUni();
    }

    Multi<Tuple2<NotificationHistory, EmailAggregationKey>> processAggregateEmailsByAggregationKey(EmailAggregationKey aggregationKey, LocalDateTime startTime, LocalDateTime endTime, EmailSubscriptionType emailSubscriptionType, boolean delete) {
        final EmailTemplate emailTemplate = emailTemplateFactory.get(aggregationKey.getBundle(), aggregationKey.getApplication());

        Multi<Tuple2<NotificationHistory, EmailAggregationKey>> doDelete = delete ?
                emailAggregationResources.purgeOldAggregation(aggregationKey, endTime)
                        .onItem().transformToMulti(unused -> Multi.createFrom().empty()) :
                Multi.createFrom().empty();

        if (!emailTemplate.isSupported(null, emailSubscriptionType)) {
            return doDelete;
        }

        TemplateInstance subject = emailTemplate.getTitle(null, emailSubscriptionType);
        TemplateInstance body = emailTemplate.getBody(null, emailSubscriptionType);

        if (subject == null || body == null) {
            return doDelete;
        }
        return emailAggregator.getAggregated(aggregationKey, emailSubscriptionType, startTime, endTime)
                .onItem().transform(Map::entrySet)
                .onItem().transformToMulti(Multi.createFrom()::iterable)
                .onItem().transformToMultiAndConcatenate(entries -> {

                    final Action action = createAction(aggregationKey, entries.getValue());
                    final User recipient = entries.getKey();

                    return emailSender.sendEmail(recipient, action, subject, body)
                            .onItem().transformToMulti(notificationHistory -> Multi.createFrom().item(Tuple2.of(notificationHistory, aggregationKey)));
                })
                .onTermination().call((throwable, aBoolean) -> {
                    if (throwable != null) {
                        log.log(Level.WARNING, "Error while processing aggregation", throwable);
                    }
                    return doDelete.toUni();
                });
    }

    private Action createAction(EmailAggregationKey aggregationKey, Map<String, Object> entries) {
        Action action = new Action();
        action.setContext(entries);
        action.setEvents(List.of());
        action.setAccountId(aggregationKey.getAccountId());
        action.setApplication(aggregationKey.getApplication());
        action.setBundle(aggregationKey.getBundle());

        // We don't have a eventtype as this aggregates over multiple event types
        action.setEventType(null);
        action.setTimestamp(LocalDateTime.now(ZoneOffset.UTC));

        return action;
    }
}
