package helpers;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;

import java.time.LocalDateTime;
import java.util.List;

import static helpers.TestHelpers.DEFAULT_ORG_ID;

public class SubscriptionsUsageTestHelpers {

    public static Action createSubscriptionsUsageAction() {
        return createSubscriptionsUsageAction("Premium", "Production");
    }

    public static Action createSubscriptionsUsageActionWithSlaOnly() {
        return createSubscriptionsUsageAction("Premium", null);
    }

    public static Action createSubscriptionsUsageActionWithUsageOnly() {
        return createSubscriptionsUsageAction(null, "Production");
    }

    public static Action createSubscriptionsUsageActionWithoutSlaAndUsage() {
        return createSubscriptionsUsageAction(null, null);
    }

    private static Action createSubscriptionsUsageAction(String serviceLevel, String usage) {
        Context.ContextBuilderBase<?> contextBuilder = Context.builder()
            .withAdditionalProperty("product_id", "RHEL for x86")
            .withAdditionalProperty("metric_id", "sockets");

        if (serviceLevel != null) {
            contextBuilder = contextBuilder.withAdditionalProperty("service_level", serviceLevel);
        }
        if (usage != null) {
            contextBuilder = contextBuilder.withAdditionalProperty("usage", usage);
        }

        return Action.builder()
            .withBundle("subscription-services")
            .withApplication("subscriptions")
            .withEventType("exceeded-utilization-threshold")
            .withOrgId(DEFAULT_ORG_ID)
            .withTimestamp(LocalDateTime.of(2020, 10, 3, 15, 22, 13, 25))
            .withContext(contextBuilder.build())
            .withEvents(List.of(
                new Event.EventBuilder()
                    .withMetadata(new Metadata.MetadataBuilder().build())
                    .withPayload(
                        new Payload.PayloadBuilder()
                            .withAdditionalProperty("utilization_percentage", "105")
                            .build()
                    )
                    .build()
            ))
            .build();
    }
}
