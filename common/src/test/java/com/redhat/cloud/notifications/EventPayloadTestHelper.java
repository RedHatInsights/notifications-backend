package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.ingress.RecipientsAuthorizationCriterion;
import com.redhat.cloud.notifications.ingress.Type;

import java.time.LocalDateTime;
import java.util.List;

public class EventPayloadTestHelper {

    public static RecipientsAuthorizationCriterion buildRecipientsAuthorizationCriterion() {
        RecipientsAuthorizationCriterion authorizationCriterion = new RecipientsAuthorizationCriterion();
        authorizationCriterion.setRelation("rel1");
        authorizationCriterion.setId("id1");
        Type kesselAssetType = new Type();
        kesselAssetType.setNamespace("namespace_test");
        kesselAssetType.setName("host");
        authorizationCriterion.setType(kesselAssetType);
        return authorizationCriterion;
    }

    public static Action buildValidAction(String orgId, String bundle, String application, String eventType) {
        Action action = new Action();
        action.setVersion("v1.0.0");
        action.setBundle(bundle);
        action.setApplication(application);
        action.setEventType(eventType);
        action.setTimestamp(LocalDateTime.now());

        action.setOrgId(orgId);
        action.setRecipients(List.of());
        action.setEvents(
            List.of(
                new com.redhat.cloud.notifications.ingress.Event.EventBuilder()
                    .withMetadata(new Metadata.MetadataBuilder().build())
                    .withPayload(new Payload.PayloadBuilder()
                        .withAdditionalProperty("k", "v")
                        .withAdditionalProperty("k2", "v2")
                        .withAdditionalProperty("k3", "v")
                        .build()
                    )
                    .build()
            )
        );

        action.setContext(new Context.ContextBuilder().build());
        return action;
    }
}
