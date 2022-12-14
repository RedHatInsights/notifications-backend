package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.transformers.BaseTransformer;
import io.vertx.core.json.JsonObject;

import java.time.LocalDateTime;
import java.util.List;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;

/*
 * Create a structure of the form:
 * {
   "bundle":"rhel",
   "application":"advisor",
   "event_type":"new-recommendation",
   "timestamp":"2022-11-30T17:30:20.773398",
   "account_id":"5758117",
   "org_id":"7806094",
   "context":{
      "inventory_id":"6ad30f3e-0497-4e74-99f1-b3f9a6120a6f",
      "hostname":"my-computer-jmartine",
      "display_name":"my-computer-jmartine",
      "rhel_version":"8.4",
      "host_url":"https://console.redhat.com/insights/inventory/6ad30f3e-0497-4e74-99f1-b3f9a6120a6f",
      "tags":[

      ]
   },
   "events":[
      {
         "metadata":{},
         "payload":{
            "rule_id":"insights_core_egg_not_up2date|INSIGHTS_CORE_EGG_NOT_UP2DATE",
            "rule_description":"System is not able to get the latest recommendations and may miss bug fixes when the Insights Client Core egg file is outdated",
            "total_risk":"2",
            "publish_date":"2021-03-13T18:44:00+00:00",
            "rule_url":"https://console.redhat.com/insights/advisor/recommendations/insights_core_egg_not_up2date|INSIGHTS_CORE_EGG_NOT_UP2DATE/",
            "reboot_required":false,
            "has_incident":false,
            "report_url":"https://console.redhat.com/insights/advisor/recommendations/insights_core_egg_not_up2date|INSIGHTS_CORE_EGG_NOT_UP2DATE/6ad30f3e-0497-4e74-99f1-b3f9a6120a6f"
         }
      }
   ]
}
 */

public class AdvisorTestHelpers {

    public static BaseTransformer baseTransformer = new BaseTransformer();

    public static final String EVENT_METADATA = "metadata";
    public static final String EVENT_PAYLOAD = "payload";
    public static final String EVENT_PAYLOAD_RULE_ID = "rule_id";
    public static final String EVENT_PAYLOAD_DESCRIPTION = "rule_description";
    public static final String EVENT_PAYLOAD_TOTAL_RISK = "total_risk";
    public static final String EVENT_PAYLOAD_URL = "total_risk";
    public static final String EVENT_PAYLOAD_PUBLISH_DATE = "publish_date";
    public static final String EVENT_PAYLOAD_REBOOT_REQUIRED = "reboot_required";
    public static final String EVENT_PAYLOAD_REPORT_URL = "report_url";

    public static Event createEvent(
        String rule_id, String description, String rule_url,
        boolean has_incident, Integer total_risk
    ) {
        /* Add events via emailActionMessage.setEvents */
        /* Fill in the important fields from the arguments, and make up
         * everything else. */
        event = new Event.EventBuilder()
            .withMetadata(new Metadata.MetadataBuilder().build())
            .withPayload(new Payload.PayloadBuilder()
                /* Supplied data */
                .withAdditionalProperty(EVENT_PAYLOAD_RULE_ID, rule_id)
                .withAdditionalProperty(EVENT_PAYLOAD_DESCRIPTION, description)
                .withAdditionalProperty(EVENT_PAYLOAD_TOTAL_RISK, total_risk.toString())
                .withAdditionalProperty(EVENT_PAYLOAD_HAS_INCIDENT, has_incident)
                .withAdditionalProperty(EVENT_PAYLOAD_URL, rule_url)
                /* Made up data */
                .withAdditionalProperty(EVENT_PAYLOAD_PUBLISH_DATE, "2021-03-13T18:44:00+00:00")
                .withAdditionalProperty(EVENT_PAYLOAD_REBOOT_REQUIRED, false)
                .withAdditionalProperty(EVENT_PAYLOAD_REPORT_URL, rule_url)
                .build()
            ).build();
        return event;
    }

    public static Action createAction(
        String bundle, String application, String eventType,
        String inventoryId, String inventoryName
    ) {
        Action emailActionMessage = new Action();
        emailActionMessage.setBundle(bundle);
        emailActionMessage.setApplication(application);
        emailActionMessage.setTimestamp(LocalDateTime.now());
        emailActionMessage.setEventType(eventType);

        emailActionMessage.setContext(
            new Context.ContextBuilder()
            .withAdditionalProperty("inventory_id", inventoryId)
            .withAdditionalProperty("system_check_in", LocalDateTime.now())
            .withAdditionalProperty("display_name", inventoryName)
            .withAdditionalProperty("tags", List.of())
            .build()
        );

        emailActionMessage.setOrgId(DEFAULT_ORG_ID);
        return emailActionMessage;
    }

    public static EmailAggregation createEmailAggregation(
        Action emailActionMessage
    ) {
        /* General process:
         * - Create an action with createAction
         * - Add one or more events to the action with
         *   setEvents(List.of(createEvent(...)))
         * - Create the Aggregation with createEmailAggregation, based on the
         *   action.
         */
        EmailAggregation aggregation = new EmailAggregation();
        aggregation.setBundleName(emailActionMessage.bundle);
        aggregation.setApplicationName(emailActionMessage.application);
        aggregation.setOrgId(DEFAULT_ORG_ID);

        JsonObject payload = baseTransformer.toJsonObject(emailActionMessage);
        aggregation.setPayload(payload);

        return aggregation;
    }
}
