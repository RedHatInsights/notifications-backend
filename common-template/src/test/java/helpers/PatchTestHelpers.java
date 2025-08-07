package helpers;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import org.apache.commons.lang3.StringUtils;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static helpers.TestHelpers.DEFAULT_ORG_ID;

public class PatchTestHelpers {

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
