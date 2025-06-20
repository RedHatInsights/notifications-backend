package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.processors.email.aggregators.ErrataEmailPayloadAggregator;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.processors.email.aggregators.ErrataEmailPayloadAggregator.EVENT_TYPE_BUGFIX;
import static com.redhat.cloud.notifications.processors.email.aggregators.ErrataEmailPayloadAggregator.EVENT_TYPE_ENHANCEMENT;
import static com.redhat.cloud.notifications.processors.email.aggregators.ErrataEmailPayloadAggregator.EVENT_TYPE_SECURITY;

public class ErrataTestHelpers {

    public static Action createErrataAction() {
        return createErrataAction(StringUtils.EMPTY);
    }

    public static Action createErrataAction(final String eventTypeName) {
        Action emailActionMessage = new Action();
        emailActionMessage.setBundle("subscription-services");
        emailActionMessage.setApplication("errata-notifications");
        emailActionMessage.setTimestamp(LocalDateTime.of(2022, 10, 3, 15, 22, 13, 25));
        emailActionMessage.setEventType(eventTypeName);
        emailActionMessage.setRecipients(List.of());

        emailActionMessage.setContext(
            new Context.ContextBuilder()
                .withAdditionalProperty("system_check_in", "2022-08-03T15:22:42.199046")
                .withAdditionalProperty("start_time", "2022-08-03T15:22:42.199046")
                .withAdditionalProperty("base_url", "https://access.redhat.com/errata/")
                .build()
        );

        emailActionMessage.setEvents(List.of(
            new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(
                    new Payload.PayloadBuilder()
                        .withAdditionalProperty("id", "RHSA-2024:" + RandomStringUtils.secure().nextNumeric(1, 5))
                        .withAdditionalProperty("severity", "Moderate")
                        .withAdditionalProperty("synopsis", "Red Hat build of Quarkus 3.8.4 release")
                        .build()
                )
                .build(),
            new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(
                    new Payload.PayloadBuilder()
                        .withAdditionalProperty("id", "RHSA-2024:" + RandomStringUtils.secure().nextNumeric(1, 5))
                        .withAdditionalProperty("severity", "Important")
                        .withAdditionalProperty("synopsis", "c-ares security update")
                        .build()
                )
                .build(),
            new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(
                    new Payload.PayloadBuilder()
                        .withAdditionalProperty("id", "RHSA-2024:" + RandomStringUtils.secure().nextNumeric(1, 5))
                        .withAdditionalProperty("severity", "Low")
                        .withAdditionalProperty("synopsis", "cockpit security update")
                        .build()
                )
                .build()
        ));

        emailActionMessage.setAccountId(StringUtils.EMPTY);
        emailActionMessage.setOrgId(DEFAULT_ORG_ID);

        return emailActionMessage;
    }

    public static Map<String, Object> buildErrataAggregatedPayload() {
        ErrataEmailPayloadAggregator aggregator = new ErrataEmailPayloadAggregator();
        aggregateEventType(aggregator, EVENT_TYPE_BUGFIX, 3);
        aggregateEventType(aggregator, EVENT_TYPE_ENHANCEMENT, 6);
        aggregateEventType(aggregator, EVENT_TYPE_SECURITY, 8);

        return aggregator.getContext();
    }

    static void aggregateEventType(final ErrataEmailPayloadAggregator aggregator, final String eventType, final int numberOfAggregations) {
        for (int i = 0; i < numberOfAggregations; i++) {
            aggregator.aggregate(TestHelpers.createEmailAggregationFromAction(ErrataTestHelpers.createErrataAction(eventType)));
        }
    }
}
