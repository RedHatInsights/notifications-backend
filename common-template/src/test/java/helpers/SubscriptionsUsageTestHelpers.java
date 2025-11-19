package helpers;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;

import java.time.LocalDateTime;
import java.util.List;

import static helpers.TestHelpers.DEFAULT_ORG_ID;

public class SubscriptionsUsageTestHelpers {

    public static Action createSubscriptionsUsageAction() {
        Action emailActionMessage = new Action();
        emailActionMessage.setBundle("subscription-services");
        emailActionMessage.setApplication("subscriptions");
        emailActionMessage.setTimestamp(LocalDateTime.of(2020, 10, 3, 15, 22, 13, 25));
        emailActionMessage.setEventType("exceeded-utilization-threshold");
        emailActionMessage.setRecipients(List.of());

        emailActionMessage.setEvents(List.of(
                new Event.EventBuilder()
                        .withMetadata(new Metadata.MetadataBuilder().build())
                        .withPayload(
                                new Payload.PayloadBuilder()
                                        .withAdditionalProperty("product_id", "RHEL for x86")
                                        .withAdditionalProperty("metric_id", "sockets")
                                        .withAdditionalProperty("utilization_percentage", "105")
                                        .build()
                        )
                        .build()
        ));

        emailActionMessage.setOrgId(DEFAULT_ORG_ID);

        return emailActionMessage;
    }
}
