package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.transformers.BaseTransformer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregator.HAS_INCIDENT;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregator.RULE_DESCRIPTION;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregator.RULE_ID;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregator.RULE_URL;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregator.TOTAL_RISK;

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

    public static final String EVENT_PAYLOAD_PUBLISH_DATE = "publish_date";
    public static final String EVENT_PAYLOAD_REBOOT_REQUIRED = "reboot_required";
    public static final String EVENT_PAYLOAD_REPORT_URL = "report_url";

    public static EmailAggregation createEmailAggregation(String eventType, Map<String, String> rule) {

        Action action = createAction(eventType, rule);

        EmailAggregation aggregation = new EmailAggregation();
        aggregation.setBundleName(action.getBundle());
        aggregation.setApplicationName(action.getApplication());
        aggregation.setOrgId(DEFAULT_ORG_ID);
        aggregation.setPayload(new BaseTransformer().toJsonObject(action));

        return aggregation;
    }

    private static Action createAction(String eventType, Map<String, String> rule) {
        return new Action.ActionBuilder()
                .withBundle("rhel")
                .withApplication("advisor")
                .withEventType(eventType)
                .withOrgId(DEFAULT_ORG_ID)
                .withTimestamp(LocalDateTime.now())
                .withContext(new Context.ContextBuilder()
                        .withAdditionalProperty("inventory_id", "6ad30f3e-0497-4e74-99f1-b3f9a6120a6f")
                        .withAdditionalProperty("display_name", "my-computer")
                        .withAdditionalProperty("tags", List.of())
                        .build()
                )
                .withEvents(List.of(new Event.EventBuilder()
                        .withMetadata(new Metadata.MetadataBuilder().build())
                        .withPayload(new Payload.PayloadBuilder()
                                /* Supplied data */
                                .withAdditionalProperty(RULE_ID, rule.get(RULE_ID))
                                .withAdditionalProperty(RULE_DESCRIPTION, rule.get(RULE_DESCRIPTION))
                                .withAdditionalProperty(TOTAL_RISK, rule.get(TOTAL_RISK))
                                .withAdditionalProperty(HAS_INCIDENT, rule.get(HAS_INCIDENT))
                                .withAdditionalProperty(RULE_URL, rule.get(RULE_URL))
                                /* Made up data */
                                .withAdditionalProperty(EVENT_PAYLOAD_PUBLISH_DATE, "2021-03-13T18:44:00+00:00")
                                .withAdditionalProperty(EVENT_PAYLOAD_REBOOT_REQUIRED, false)
                                .withAdditionalProperty(EVENT_PAYLOAD_REPORT_URL, "https://console.redhat.com")
                                .build()
                        ).build()))
                .build();
    }
}
