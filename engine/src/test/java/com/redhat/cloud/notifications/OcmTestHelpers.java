package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;

public class OcmTestHelpers {

    public static Action createOcmAction(String clusterDisplayName, String subscriptionPlan, String logDescription, String subject) {
        return createOcmAction(clusterDisplayName, subscriptionPlan, logDescription, subject, null, Optional.empty());
    }

    public static Action createOcmAction(String clusterDisplayName, String subscriptionPlan, String logDescription, String subject, String title, Optional<Map<String, Object>> specificGlobalVars) {
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
        Map<String, Object> globalVars = new HashMap<>();
        globalVars.put("cluster_display_name", clusterDisplayName);
        globalVars.put("subscription_id", "2XqNHRdLNEAzshh7MkkOql6fx6I");
        globalVars.put("subscription_plan", subscriptionPlan);
        globalVars.put("log_description", logDescription);
        globalVars.put("internal_cluster_id", "fekelklflef");

        if (specificGlobalVars.isPresent()) {
            globalVars.putAll(specificGlobalVars.get());
        }

        emailActionMessage.setEvents(List.of(
                new Event.EventBuilder()
                        .withMetadata(new Metadata.MetadataBuilder().build())
                        .withPayload(
                                new Payload.PayloadBuilder()
                                    .withAdditionalProperty("global_vars", globalVars)
                                    .withAdditionalProperty("subject", subject)
                                    .withAdditionalProperty("title", title)
                                    .build()
                        )
                        .build()
        ));

        emailActionMessage.setOrgId(DEFAULT_ORG_ID);

        return emailActionMessage;
    }
}
