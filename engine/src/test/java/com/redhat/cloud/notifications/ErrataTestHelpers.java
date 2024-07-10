package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import org.apache.commons.lang3.StringUtils;
import java.time.LocalDateTime;
import java.util.List;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;

public class ErrataTestHelpers {

    public static Action createErrataAction() {
        Action emailActionMessage = new Action();
        emailActionMessage.setBundle(StringUtils.EMPTY);
        emailActionMessage.setApplication(StringUtils.EMPTY);
        emailActionMessage.setTimestamp(LocalDateTime.of(2022, 10, 3, 15, 22, 13, 25));
        emailActionMessage.setEventType(StringUtils.EMPTY);
        emailActionMessage.setRecipients(List.of());

        emailActionMessage.setContext(
            new Context.ContextBuilder()
                .withAdditionalProperty("system_check_in", "2022-08-03T15:22:42.199046")
                .withAdditionalProperty("start_time", "2022-08-03T15:22:42.199046")
                .build()
        );

        emailActionMessage.setEvents(List.of(
            new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(
                    new Payload.PayloadBuilder()
                        .withAdditionalProperty("id", "RHSA-2024:2106")
                        .withAdditionalProperty("synopsis", "Moderate: Red Hat build of Quarkus 3.8.4 release")
                        .withAdditionalProperty("url", "https://access.redhat.com/errata/RHSA-2024:2106")
                        .build()
                )
                .build(),
            new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(
                    new Payload.PayloadBuilder()
                        .withAdditionalProperty("id", "RHSA-2024:3842")
                        .withAdditionalProperty("synopsis", "Low: c-ares security update")
                        .withAdditionalProperty("url", "https://access.redhat.com/errata/RHSA-2024:3842")
                        .build()
                )
                .build(),
            new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(
                    new Payload.PayloadBuilder()
                        .withAdditionalProperty("id", "RHSA-2024:3843")
                        .withAdditionalProperty("synopsis", "Moderate: cockpit security update")
                        .withAdditionalProperty("url", "https://access.redhat.com/errata/RHSA-2024:3843")
                        .build()
                )
                .build()
        ));

        emailActionMessage.setAccountId(StringUtils.EMPTY);
        emailActionMessage.setOrgId(DEFAULT_ORG_ID);

        return emailActionMessage;
    }
}
