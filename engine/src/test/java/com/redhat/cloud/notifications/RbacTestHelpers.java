package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import org.apache.commons.lang3.StringUtils;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;

public class RbacTestHelpers {

    public static Action createRbacAction() {
        Action emailActionMessage = new Action();
        emailActionMessage.setBundle(StringUtils.EMPTY);
        emailActionMessage.setApplication(StringUtils.EMPTY);
        emailActionMessage.setTimestamp(LocalDateTime.of(2020, 10, 3, 15, 22, 13, 25));
        emailActionMessage.setEventType(StringUtils.EMPTY);
        emailActionMessage.setRecipients(List.of());

        emailActionMessage.setContext(
            new Context.ContextBuilder()
                .withAdditionalProperty("system_check_in", "2020-08-03T15:22:42.199046")
                .build()
        );
        emailActionMessage.setEvents(List.of(
            new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(
                    new Payload.PayloadBuilder()
                        .withAdditionalProperty("name", "testRoleName")
                        .withAdditionalProperty("role", new TestRole("myRole"))
                        .withAdditionalProperty("username", "testUser1")
                        .withAdditionalProperty("principal", "testUser1")
                        .withAdditionalProperty("operation", "added")
                        .build()
                )
                .build()
        ));

        emailActionMessage.setAccountId(StringUtils.EMPTY);
        emailActionMessage.setOrgId(DEFAULT_ORG_ID);

        return emailActionMessage;
    }

    private static class TestRole {
        public String name;
        public UUID uuid = UUID.randomUUID();

        TestRole(String name) {
            this.name = name;
        }
    }
}
