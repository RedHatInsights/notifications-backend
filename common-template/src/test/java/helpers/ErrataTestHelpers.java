package helpers;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ErrataTestHelpers {
    public static final String BUGFIX_ERRATA = "new-subscription-bugfix-errata";
    public static final String SECURITY_ERRATA = "new-subscription-security-errata";
    public static final String ENHANCEMENT_ERRATA = "new-subscription-enhancement-errata";

    private static final String ERRATA_SEARCH_URL = "https://access.redhat.com/errata-search/?from=notifications&integration=";

    public static Action createErrataAction() {
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
                        .withAdditionalProperty("base_url", "https://access.redhat.com/errata/")
                        .build()
        );

        emailActionMessage.setEvents(List.of(
                new Event.EventBuilder()
                        .withMetadata(new Metadata.MetadataBuilder().build())
                        .withPayload(
                                new Payload.PayloadBuilder()
                                        .withAdditionalProperty("id", "RHSA-2024:2106")
                                        .withAdditionalProperty("severity", "Moderate")
                                        .withAdditionalProperty("synopsis", "Red Hat build of Quarkus 3.8.4 release")
                                        .build()
                        )
                        .build(),
                new Event.EventBuilder()
                        .withMetadata(new Metadata.MetadataBuilder().build())
                        .withPayload(
                                new Payload.PayloadBuilder()
                                        .withAdditionalProperty("id", "RHSA-2024:3842")
                                        .withAdditionalProperty("severity", "Important")
                                        .withAdditionalProperty("synopsis", "c-ares security update")
                                        .build()
                        )
                        .build(),
                new Event.EventBuilder()
                        .withMetadata(new Metadata.MetadataBuilder().build())
                        .withPayload(
                                new Payload.PayloadBuilder()
                                        .withAdditionalProperty("id", "RHSA-2024:3843")
                                        .withAdditionalProperty("severity", "Low")
                                        .withAdditionalProperty("synopsis", "cockpit security update")
                                        .build()
                        )
                        .build(),
                new Event.EventBuilder()
                    .withMetadata(new Metadata.MetadataBuilder().build())
                    .withPayload(
                        new Payload.PayloadBuilder()
                            .withAdditionalProperty("id", "RHSA-2025:7174")
                            .withAdditionalProperty("severity", "Important")
                            .withAdditionalProperty("synopsis", "sanlock bug fix and enhancement update")
                            .build()
                )
                .build()
        ));

        emailActionMessage.setAccountId(StringUtils.EMPTY);
        emailActionMessage.setOrgId(DEFAULT_ORG_ID);

        return emailActionMessage;
    }

    public static void checkErrataChatTemplateContent(String eventType, String result, Action action, String integrationUrlTag) {
        String expectedEventTypeLabel = switch (eventType) {
            case BUGFIX_ERRATA -> "bug fixes";
            case SECURITY_ERRATA -> "security updates";
            case ENHANCEMENT_ERRATA -> "enhancements";
            default -> throw new IllegalArgumentException(eventType + "is not a valid event type");
        };

        assertTrue(result.contains(
            String.format("There are %d %s affecting your subscriptions.", action.getEvents().size(), expectedEventTypeLabel)));
        assertTrue(result.contains("First 3 are:"));
        for (int i = 0; i < 3; i++) {
            assertTrue(result.contains(action.getEvents().get(i).getPayload().getAdditionalProperties().get("id").toString()));
            assertTrue(result.contains(action.getEvents().get(i).getPayload().getAdditionalProperties().get("synopsis").toString()));
        }
        assertFalse(result.contains(action.getEvents().get(3).getPayload().getAdditionalProperties().get("id").toString()));
        assertTrue(result.contains(ERRATA_SEARCH_URL + integrationUrlTag));
    }
}
