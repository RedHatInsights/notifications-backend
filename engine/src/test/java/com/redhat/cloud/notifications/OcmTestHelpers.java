package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;

public class OcmTestHelpers {

    public static Action createOcmAction(String clusterDisplayName, String subscriptionPlan, String logDescription, String upgradeStatus, String subject) {
        Action emailActionMessage = new Action();
        emailActionMessage.setBundle("openshift");
        emailActionMessage.setApplication("cluster-manager");
        emailActionMessage.setTimestamp(LocalDateTime.now());
        emailActionMessage.setEventType("testEmailSubscriptionInstant");

        emailActionMessage.setContext(
                new Context.ContextBuilder()
                        .withAdditionalProperty("system_check_in", "2021-07-13T15:22:42.199046")
                        .withAdditionalProperty("tags", List.of())
                        .build()
        );
        Map<String, String> globalVars = Map.of(
            "cluster_display_name", clusterDisplayName,
            "subscription_id", "2XqNHRdLNEAzshh7MkkOql6fx6I",
            "subscription_plan", subscriptionPlan,
            "log_description", logDescription,
            "upgrade_status", upgradeStatus
        );
        emailActionMessage.setEvents(List.of(
                new Event.EventBuilder()
                        .withMetadata(new Metadata.MetadataBuilder().build())
                        .withPayload(
                                new Payload.PayloadBuilder()
                                    .withAdditionalProperty("global_vars", globalVars)
                                    .withAdditionalProperty("subject", subject)
                                    .build()
                        )
                        .build()
        ));

        emailActionMessage.setOrgId(DEFAULT_ORG_ID);

        return emailActionMessage;
    }
}
