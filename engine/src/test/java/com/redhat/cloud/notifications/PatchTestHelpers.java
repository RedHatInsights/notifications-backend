package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.processors.email.aggregators.PatchEmailPayloadAggregator;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;

public class PatchTestHelpers {

    private static final String ADVISORY_NAME = "advisory_name";
    private static final String SYNOPSIS = "synopsis";
    private static final String ADVISORY_TYPE = "advisory_type";
    private static final String UNIQUE_HOSTS_CNT = "unique_system_count";
    private static final String ADVISORIES_KEY = "advisories";

    public static EmailAggregation createEmailAggregation(String bundle, String application, String advisoryName, String synopsis, String advisoryType, String inventory_id) {
        EmailAggregation aggregation = new EmailAggregation();
        aggregation.setBundleName(bundle);
        aggregation.setApplicationName(application);
        aggregation.setOrgId(DEFAULT_ORG_ID);

        Action emailActionMessage = new Action();
        emailActionMessage.setBundle(bundle);
        emailActionMessage.setApplication(application);
        emailActionMessage.setTimestamp(LocalDateTime.now());
        emailActionMessage.setEventType("new-advisory");
        emailActionMessage.setContext(
                new Context.ContextBuilder()
                        .withAdditionalProperty("inventory_id", inventory_id)
                        .build()
        );
        emailActionMessage.setEvents(List.of(
                new Event.EventBuilder()
                        .withMetadata(new Metadata.MetadataBuilder().build())
                        .withPayload(
                                new Payload.PayloadBuilder()
                                        .withAdditionalProperty(ADVISORY_NAME, advisoryName)
                                        .withAdditionalProperty(SYNOPSIS, synopsis)
                                        .withAdditionalProperty(ADVISORY_TYPE, advisoryType)
                                        .build()
                        )
                        .build()
        ));
        emailActionMessage.setOrgId(DEFAULT_ORG_ID);

        aggregation.setPayload(TestHelpers.wrapActionToJsonObject(emailActionMessage));

        return aggregation;
    }

    public static EmailAggregation createEmailAggregationMultipleEvents(String bundle, String application) {
        EmailAggregation aggregation = new EmailAggregation();
        aggregation.setBundleName(bundle);
        aggregation.setApplicationName(application);
        aggregation.setOrgId(DEFAULT_ORG_ID);

        Action emailActionMessage = new Action();
        emailActionMessage.setBundle(bundle);
        emailActionMessage.setApplication(application);
        emailActionMessage.setTimestamp(LocalDateTime.now());
        emailActionMessage.setEventType("new-advisory");
        emailActionMessage.setContext(
                new Context.ContextBuilder()
                        .withAdditionalProperty("inventory_id", "inventory_id")
                        .build()
        );
        emailActionMessage.setEvents(List.of(
                new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(
                        new Payload.PayloadBuilder()
                                .withAdditionalProperty(ADVISORY_NAME, "RH-1")
                                .withAdditionalProperty(SYNOPSIS, "synopsis")
                                .withAdditionalProperty(ADVISORY_TYPE, "ENHANCEMENT")
                                .build()
                )
                .build(),
                new Event.EventBuilder()
                        .withMetadata(new Metadata.MetadataBuilder().build())
                        .withPayload(
                                new Payload.PayloadBuilder()
                                        .withAdditionalProperty(ADVISORY_NAME, "RH-2")
                                        .withAdditionalProperty(SYNOPSIS, "synopsis")
                                        .withAdditionalProperty(ADVISORY_TYPE, "BUGFIX")
                                        .build()
                        )
                .build(),
                new Event.EventBuilder()
                        .withMetadata(new Metadata.MetadataBuilder().build())
                        .withPayload(
                                new Payload.PayloadBuilder()
                                        .withAdditionalProperty(ADVISORY_NAME, "RH-3")
                                        .withAdditionalProperty(SYNOPSIS, "synopsis")
                                        .withAdditionalProperty(ADVISORY_TYPE, "UNKNOWN")
                                        .build()
                        )
                .build()
        ));
        emailActionMessage.setOrgId(DEFAULT_ORG_ID);

        aggregation.setPayload(TestHelpers.wrapActionToJsonObject(emailActionMessage));

        return aggregation;
    }

    public static ArrayList<LinkedHashMap<String, String>> getAdvisoriesByType(PatchEmailPayloadAggregator aggregator, String advisoryType) {
        Map<String, Object> patch = (Map<String, Object>) ((Map<String, Object>) aggregator.getContext()).get("patch");
        return (ArrayList<LinkedHashMap<String, String>>) patch.get(advisoryType);
    }


    public static Action createPatchAction() {
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
                .withAdditionalProperty("patch", Map.of("Numerical", List.of("adv1", "adv2"), "Roman", List.of("advI", "advII", "advIII"), "Alpha", List.of("advA", "advB", "advC")))
                .build()
        );

        emailActionMessage.setEvents(List.of(
            new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(
                    new Payload.PayloadBuilder()
                        .withAdditionalProperty("advisory_name", "name 1")
                        .withAdditionalProperty("synopsis", "synopsis 1")
                        .build()
                )
                .build(),
            new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(
                    new Payload.PayloadBuilder()
                        .withAdditionalProperty("advisory_name", "name 2")
                        .withAdditionalProperty("synopsis", "synopsis 2")
                        .build()
                )
                .build()
        ));

        emailActionMessage.setAccountId(StringUtils.EMPTY);
        emailActionMessage.setOrgId(DEFAULT_ORG_ID);

        return emailActionMessage;
    }


}
