package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.models.EmailAggregation;

import java.time.LocalDateTime;
import java.util.List;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static java.time.ZoneOffset.UTC;

public class ComplianceTestHelpers {

    public static EmailAggregation createEmailAggregation(String bundle, String application, String eventType, String policyId, String inventoryId) {
        EmailAggregation aggregation = new EmailAggregation();
        aggregation.setBundleName(bundle);
        aggregation.setApplicationName(application);
        aggregation.setOrgId(DEFAULT_ORG_ID);
        aggregation.setCreated(LocalDateTime.now(UTC).minusHours(5L));

        Action emailActionMessage = new Action();
        emailActionMessage.setBundle(bundle);
        emailActionMessage.setApplication(application);
        emailActionMessage.setTimestamp(LocalDateTime.now());
        emailActionMessage.setEventType(eventType);

        emailActionMessage.setContext(
                new Context.ContextBuilder()
                        .withAdditionalProperty("inventory_id", inventoryId)
                        .withAdditionalProperty("system_check_in", "2020-08-03T15:22:42.199046")
                        .withAdditionalProperty("display_name", "My test machine")
                        .withAdditionalProperty("tags", List.of())
                        .build()
        );
        emailActionMessage.setEvents(List.of(
                new Event.EventBuilder()
                        .withMetadata(new Metadata.MetadataBuilder().build())
                        .withPayload(
                                new Payload.PayloadBuilder()
                                        .withAdditionalProperty("policy_id", policyId)
                                        .withAdditionalProperty("policy_name", "not-tested-name")
                                        .withAdditionalProperty("policy_description", "not-used-desc")
                                        .withAdditionalProperty("policy_condition", "not-used-condition")
                                        .build()
                        )
                        .build()
        ));

        emailActionMessage.setOrgId(DEFAULT_ORG_ID);
        aggregation.setPayload(TestHelpers.wrapActionToJsonObject(emailActionMessage));

        return aggregation;
    }

}
