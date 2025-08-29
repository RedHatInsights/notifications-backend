package helpers;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;

import java.time.LocalDateTime;
import java.util.List;

import static helpers.TestHelpers.DEFAULT_ORG_ID;

public class SubscriptionWatchTestHelpers {

    public static Action createSubscriptionWatchAction() {
        Action emailActionMessage = new Action();
        emailActionMessage.setBundle("subscription-services");
        emailActionMessage.setApplication("subscription-watch");
        emailActionMessage.setTimestamp(LocalDateTime.of(2020, 10, 3, 15, 22, 13, 25));
        emailActionMessage.setEventType("usage-threshold-exceeded");
        emailActionMessage.setRecipients(List.of());

        emailActionMessage.setContext(
                new Context.ContextBuilder()
                        .withAdditionalProperty("org_id", "123456789")
                        .withAdditionalProperty("product_tag", "RHEL for x86")
                        .withAdditionalProperty("threshold_percentage", "85")
                        .build()
        );

        emailActionMessage.setEvents(List.of(
                new Event.EventBuilder()
                        .withMetadata(new Metadata.MetadataBuilder().build())
                        .withPayload(
                                new Payload.PayloadBuilder()
                                        .withAdditionalProperty("org_id", "123456789")
                                        .withAdditionalProperty("product_tag", "RHEL for x86")
                                        .withAdditionalProperty("threshold_percentage", "85")
                                        .build()
                        )
                        .build()
        ));

        emailActionMessage.setOrgId(DEFAULT_ORG_ID);

        return emailActionMessage;
    }
}
