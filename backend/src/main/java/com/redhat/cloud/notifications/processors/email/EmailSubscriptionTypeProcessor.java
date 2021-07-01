package com.redhat.cloud.notifications.processors.email;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.redhat.cloud.notifications.db.EmailAggregationResources;
import com.redhat.cloud.notifications.db.EndpointEmailSubscriptionResources;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import com.redhat.cloud.notifications.models.EmailSubscription;
import com.redhat.cloud.notifications.models.EmailSubscriptionProperties;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Notification;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.processors.EndpointTypeProcessor;
import com.redhat.cloud.notifications.processors.email.aggregators.AbstractEmailPayloadAggregator;
import com.redhat.cloud.notifications.processors.email.aggregators.EmailPayloadAggregatorFactory;
import com.redhat.cloud.notifications.processors.email.bop.Email;
import com.redhat.cloud.notifications.processors.webclient.SslVerificationDisabled;
import com.redhat.cloud.notifications.processors.webhooks.WebhookTypeProcessor;
import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.recipients.rbac.RbacRecipientUsersProvider;
import com.redhat.cloud.notifications.templates.EmailTemplate;
import com.redhat.cloud.notifications.templates.EmailTemplateFactory;
import com.redhat.cloud.notifications.transformers.BaseTransformer;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.ScheduledExecution;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.HttpRequest;
import io.vertx.mutiny.ext.web.client.WebClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@ApplicationScoped
public class EmailSubscriptionTypeProcessor implements EndpointTypeProcessor {

    private final Logger log = Logger.getLogger(this.getClass().getName());
    private final ZoneOffset UTC = ZoneOffset.UTC;

    static final String BOP_APITOKEN_HEADER = "x-rh-apitoken";
    static final String BOP_CLIENT_ID_HEADER = "x-rh-clientid";
    static final String BOP_ENV_HEADER = "x-rh-insights-env";

    static final String BODY_TYPE_HTML = "html";

    @Inject
    @SslVerificationDisabled
    WebClient unsecuredWebClient;

    @Inject
    WebhookTypeProcessor webhookSender;

    @Inject
    EndpointEmailSubscriptionResources subscriptionResources;

    @Inject
    RbacRecipientUsersProvider rbacRecipientUsersProvider;

    @Inject
    EmailAggregationResources emailAggregationResources;

    @Inject
    BaseTransformer baseTransformer;

    @Inject
    EmailTemplateFactory emailTemplateFactory;

    @ConfigProperty(name = "processor.email.bop_url")
    String bopUrl;

    @ConfigProperty(name = "processor.email.bop_apitoken")
    String bopApiToken;

    @ConfigProperty(name = "processor.email.bop_client_id")
    String bopClientId;

    @ConfigProperty(name = "processor.email.bop_env")
    String bopEnv;

    @ConfigProperty(name = "processor.email.no_reply")
    String noReplyAddress;

    protected HttpRequest<Buffer> buildBOPHttpRequest() {
        return unsecuredWebClient
                .postAbs(bopUrl)
                .putHeader(BOP_APITOKEN_HEADER, bopApiToken)
                .putHeader(BOP_CLIENT_ID_HEADER, bopClientId)
                .putHeader(BOP_ENV_HEADER, bopEnv);
    }

    protected Email buildEmail(Set<String> recipients) {
        Email email = new Email();
        email.setBodyType(BODY_TYPE_HTML);
        email.setCcList(Set.of());
        email.setRecipients(Set.of(noReplyAddress));
        email.setBccList(recipients);
        return email;
    }

    static class Emails {
        @JsonProperty("emails")
        private Set<Email> emails;

        Emails() {
            emails = new HashSet<>();
        }

        public void addEmail(Email email) {
            emails.add(email);
        }

        public Set<Email> getEmails() {
            return emails;
        }
    }

    @Override
    public Multi<NotificationHistory> process(Action action, List<Endpoint> endpoints) {
        if (endpoints == null || endpoints.isEmpty()) {
            return Multi.createFrom().empty();
        } else {
            final EmailTemplate template = emailTemplateFactory.get(action.getBundle(), action.getApplication());
            final boolean shouldSaveAggregation = Arrays.stream(EmailSubscriptionType.values())
                    .filter(emailSubscriptionType -> emailSubscriptionType != EmailSubscriptionType.INSTANT)
                    .anyMatch(emailSubscriptionType -> template.isSupported(action.getEventType(), emailSubscriptionType));

            Uni<Boolean> processUni;

            if (shouldSaveAggregation) {
                EmailAggregation aggregation = new EmailAggregation();
                aggregation.setAccountId(action.getAccountId());
                aggregation.setApplicationName(action.getApplication());
                aggregation.setBundleName(action.getBundle());

                processUni = baseTransformer.transform(action)
                        .onItem().transform(transformedAction -> {
                            aggregation.setPayload(transformedAction);
                            return aggregation;
                        })
                        .onItem().transformToUni(emailAggregation -> this.emailAggregationResources.addEmailAggregation(emailAggregation));
            } else {
                processUni = Uni.createFrom().item(false);
            }

            return processUni.onItem().transformToUni(_unused -> sendEmail(
                    action,
                    endpoints,
                    EmailSubscriptionType.INSTANT
            )).toMulti();
        }
    }

    private Uni<List<User>> recipientUsers(String accountId, Endpoint endpoint, Set<String> subscribers) {
        final EmailSubscriptionProperties props = (EmailSubscriptionProperties) endpoint.getProperties();

        Uni<List<User>> usersUni;
        if (props.getGroupId() == null) {
            usersUni = rbacRecipientUsersProvider.getUsers(accountId, props.getOnlyAdmins());
        } else {
            usersUni = rbacRecipientUsersProvider.getGroupUsers(accountId, props.getOnlyAdmins(), props.getGroupId());
        }

        return usersUni.onItem().transform(users -> {
            if (props.getIgnorePreferences()) {
                return users;
            }

            return users.stream().filter(user -> subscribers.contains(user.getUsername())).collect(Collectors.toList());
        });
    }

    private Uni<List<User>> recipientUsers(String accountId, List<Endpoint> endpoints, Set<String> subscribers) {
        return Multi.createFrom().iterable(endpoints)
                .onItem().transformToUni(e -> recipientUsers(accountId, e, subscribers))
                .concatenate().collect().in(ArrayList<User>::new, List::addAll)
                .onItem().transform(users -> users.stream().distinct().collect(Collectors.toList()));
    }

    private Uni<NotificationHistory> sendEmail(Action action, List<Endpoint> endpoints, EmailSubscriptionType emailSubscriptionType) {
        final HttpRequest<Buffer> bopRequest = this.buildBOPHttpRequest();

        subscriptionResources
                .getEmailSubscribers(action.getAccountId(), action.getBundle(), action.getApplication(), emailSubscriptionType)
                .onItem().transformToUni(emailSubscriptions -> {
                    Set<String> subscribers = emailSubscriptions.stream().map(EmailSubscription::getUserId).collect(Collectors.toSet());
                    return recipientUsers(action.getAccountId(), endpoints, subscribers);
                })
        .onItem().transform(users -> users.stream().map(User::getUsername).distinct().collect(Collectors.toSet()))
        // Todo: We need to start updating here if we want personalized emails. The following block builds the Email object for sending to BOP from a set
        .onItem().transform(users -> {
            if (users.size() > 0) {
                return buildEmail(users);
            }

            return null;
        })
        .onItem().transformToUni(email -> {
                if (email == null) {
                    return Uni.createFrom().nullItem();
                }

                EmailTemplate emailTemplate = emailTemplateFactory.get(action.getBundle(), action.getApplication());

                if (emailTemplate.isSupported(action.getEventType(), emailSubscriptionType)) {
                    Uni<String> title = emailTemplate.getTitle(action.getEventType(), emailSubscriptionType)
                            .data("action", action)
                            .createMulti()
                            .collect().with(Collectors.joining())
                            .onFailure()
                            .recoverWithItem(templateEx -> {
                                log.log(Level.WARNING, templateEx, () -> String.format(
                                        "Unable to render template title for application: [%s], eventType: [%s], subscriptionType: [%s].",
                                        action.getApplication(),
                                        action.getEventType(),
                                        emailSubscriptionType
                                ));
                                return null;
                            });

                    Uni<String> body = emailTemplate.getBody(action.getEventType(), emailSubscriptionType)
                            .data("action", action)
                            .createMulti()
                            .collect().with(Collectors.joining())
                            .onFailure()
                            .recoverWithItem(templateEx -> {
                                log.log(Level.WARNING, templateEx, () -> String.format(
                                        "Unable to render template body for application: [%s], eventType: [%s], subscriptionType: [%s].",
                                        action.getApplication(),
                                        action.getEventType(),
                                        emailSubscriptionType
                                ));
                                return null;
                            });

                    return Uni.combine().all()
                            .unis(
                                    Uni.createFrom().item(email),
                                    title,
                                    body
                            ).asTuple()
                            .onItem().transform(objects -> {
                                if (objects == null || objects.getItem1() == null || objects.getItem2() == null || objects.getItem3() == null) {
                                    return null;
                                }

                                return objects;
                            });
                }

                return Uni.createFrom().nullItem();
            })
            .onItem().transform(data -> {
                if (data != null) {
                    Email email = data.getItem1();
                    String title = data.getItem2();
                    String body = data.getItem3();
                    email.setSubject(title);
                    email.setBody(body);

                    return email;
                }

                return null;
            })
            .onItem().transformToUni(email -> {
                if (email == null) {
                    return Uni.createFrom().nullItem();
                }

                Emails emails = new Emails();
                emails.addEmail(email);
                Uni<JsonObject> payload = Uni.createFrom().item(JsonObject.mapFrom(emails));

                // TODO Add recipients processing from policies-notifications processing (failed recipients)
                //      by checking the NotificationHistory's details section (if missing payload - fix in WebhookTypeProcessor)

                // TODO If the call fails - we should probably rollback Kafka topic (if BOP is down for example)
                //      also add metrics for these failures

                // So we are about to send the email
                // but since we do de-duplication we have multiple email endpoints but only one request.
                // Should we
                // a) Save the NotificationHistory on the related endpoints (refactoring doHttpRequest method)
                // b) Skip saving the NotificationHistory (refactoring doHttpRequest)
                // c) Have a "default" email endpoint whose only purpose is to save all the NotificationHistory
                // d) Pick any?

                return webhookSender.doHttpRequest(item, bopRequest, payload);
            });
    }

    private Multi<Tuple2<NotificationHistory, EmailAggregationKey>> processAggregateEmailsByAggregationKey(EmailAggregationKey aggregationKey, LocalDateTime startTime, LocalDateTime endTime, EmailSubscriptionType emailSubscriptionType, boolean delete) {
        return subscriptionResources.getEmailSubscribersCount(aggregationKey.getAccountId(), aggregationKey.getBundle(), aggregationKey.getApplication(), emailSubscriptionType)
                .onItem().transformToMulti(subscriberCount -> {
                    AbstractEmailPayloadAggregator aggregator = EmailPayloadAggregatorFactory.by(aggregationKey);

                    if (subscriberCount > 0 && aggregator != null) {
                        return emailAggregationResources.getEmailAggregation(aggregationKey, startTime, endTime)
                                .onItem().transformToMulti(Multi.createFrom()::iterable)
                                .collect().in(() -> aggregator, AbstractEmailPayloadAggregator::aggregate).toMulti();
                    }

                    if (delete) {
                        // Nothing to do, delete them right away.
                        return emailAggregationResources.purgeOldAggregation(aggregationKey, endTime)
                                .toMulti()
                                .onItem().transformToMultiAndMerge(i -> Multi.createFrom().empty());
                    }

                    return Multi.createFrom().empty();
                })
                .onItem().transformToMultiAndConcatenate(aggregator -> {
                    String accountId = aggregationKey.getAccountId();
                    String bundle = aggregationKey.getBundle();
                    String application = aggregationKey.getApplication();

                    if (aggregator.getProcessedAggregations() == 0) {
                        return Multi.createFrom().empty();
                    }

                    aggregator.setStartTime(startTime);
                    aggregator.setEndTimeKey(endTime);
                    Action action = new Action();
                    action.setContext(aggregator.getContext());
                    action.setEvents(List.of());
                    action.setAccountId(accountId);
                    action.setApplication(application);
                    action.setBundle(bundle);

                    // We don't have a eventtype as this aggregates over multiple event types
                    action.setEventType(null);
                    action.setTimestamp(LocalDateTime.now(ZoneOffset.UTC));

                    // We don't have any endpoint (yet) as this aggregates multiple endpoints
                    Notification item = new Notification(action, null);

                    return sendEmail(item, emailSubscriptionType).onItem().transformToMulti(notificationHistory -> Multi.createFrom().item(Tuple2.of(notificationHistory, aggregationKey)));
                })
                .onItem().transformToMultiAndConcatenate(result -> {
                    if (delete) {
                        return emailAggregationResources.purgeOldAggregation(aggregationKey, endTime)
                                .toMulti()
                                .onItem().transform(integer -> result);
                    }

                    return Multi.createFrom().item(result);
                });
                // Todo: If we want to save the NotificationHistory, this could be a good place to do so. We would probably require a special EndpointType
                // .onItem().invoke(result -> { })
    }

    Uni<List<Tuple2<NotificationHistory, EmailAggregationKey>>> processAggregateEmails(Instant scheduledFireTime, EmailSubscriptionType emailSubscriptionType, boolean delete) {
        Instant yesterdayScheduledFireTime = scheduledFireTime.minus(emailSubscriptionType.getDuration());

        LocalDateTime endTime = LocalDateTime.ofInstant(scheduledFireTime, UTC);
        LocalDateTime startTime = LocalDateTime.ofInstant(yesterdayScheduledFireTime, UTC);
        final LocalDateTime aggregateStarted = LocalDateTime.now();

        log.info(String.format("Running %s email aggregation for period (%s, %s)", emailSubscriptionType.toString(), startTime.toString(), endTime.toString()));

        return emailAggregationResources.getApplicationsWithPendingAggregation(startTime, endTime)
                .onItem().transformToMulti(Multi.createFrom()::iterable)
                .onItem().transformToMultiAndConcatenate(aggregationKey -> processAggregateEmailsByAggregationKey(aggregationKey, startTime, endTime, emailSubscriptionType, delete))
                .collect().asList()
                .onItem().invoke(result -> {
                    final LocalDateTime aggregateFinished = LocalDateTime.now();
                    log.info(
                            String.format(
                                    "Finished running %s email aggregation for period (%s, %s) after %d seconds. %d (accountIds, applications) pairs were processed",
                                    emailSubscriptionType.toString(),
                                    startTime.toString(),
                                    endTime.toString(),
                                    ChronoUnit.SECONDS.between(aggregateStarted, aggregateFinished),
                                    result.size()
                            )
                    );
                });
    }

    @Scheduled(identity = "dailyEmailProcessor", cron = "{email.subscription.daily.cron}")
    public void processDailyEmail(ScheduledExecution se) {
        // Only delete on the largest aggregate time frame. Currently daily.
        processAggregateEmails(se.getScheduledFireTime(), EmailSubscriptionType.DAILY, true).await().indefinitely();
    }

}
