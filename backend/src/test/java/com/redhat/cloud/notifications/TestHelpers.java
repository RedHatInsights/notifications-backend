package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Parser;
import com.redhat.cloud.notifications.ingress.Payload;
import io.restassured.http.Header;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.time.LocalDateTime;
import java.util.List;

import static com.redhat.cloud.notifications.Constants.X_RH_IDENTITY_HEADER;

public class TestHelpers {

    public static final String policyId1 = "abcd-efghi-jkl-lmn";
    public static final String policyName1 = "Foobar";
    public static final String policyId2 = "0123-456-789-5721f";
    public static final String policyName2 = "Latest foo is installed";
    public static final String eventType = "test-email-subscription-instant";

    public static String encodeRHIdentityInfo(String tenant, String username) {
        JsonObject identity = new JsonObject();
        JsonObject user = new JsonObject();
        user.put("username", username);
        identity.put("account_number", tenant);
        identity.put("user", user);
        identity.put("type", "User");
        JsonObject header = new JsonObject();
        header.put("identity", identity);

        return Base64Utils.encode(header.encode());
    }

    public static String encodeTurnpikeIdentityInfo(String username, String... groups) {
        JsonObject identity = new JsonObject();
        JsonObject associate = new JsonObject();
        JsonArray roles = new JsonArray();

        identity.put("auth_type", "saml-auth");
        identity.put("type", "Associate");
        identity.put("associate", associate);
        associate.put("email", username);
        associate.put("Role", roles);
        for (String group: groups) {
            roles.add(group);
        }

        JsonObject header = new JsonObject();
        header.put("identity", identity);

        return Base64Utils.encode(header.encode());
    }

    public static Header createRHIdentityHeader(String tenant, String username) {
        return new Header(X_RH_IDENTITY_HEADER, encodeRHIdentityInfo(tenant, username));
    }

    public static Header createTurnpikeIdentityHeader(String username, String... roles) {
        return new Header(X_RH_IDENTITY_HEADER, encodeTurnpikeIdentityInfo(username, roles));
    }

    public static Header createRHIdentityHeader(String encodedIdentityHeader) {
        return new Header(X_RH_IDENTITY_HEADER, encodedIdentityHeader);
    }

    public static String serializeAction(Action action) {
        return Parser.encode(action);
    }

    public static Action createPoliciesAction(String accountId, String bundle, String application, String hostDisplayName) {
        Action emailActionMessage = new Action();
        emailActionMessage.setBundle(bundle);
        emailActionMessage.setApplication(application);
        emailActionMessage.setTimestamp(LocalDateTime.of(2020, 10, 3, 15, 22, 13, 25));
        emailActionMessage.setEventType(eventType);
        emailActionMessage.setRecipients(List.of());

        emailActionMessage.setContext(
                new Context.ContextBuilder()
                        .withAdditionalProperty("inventory_id", "host-01")
                        .withAdditionalProperty("system_check_in", "2020-08-03T15:22:42.199046")
                        .withAdditionalProperty("display_name", hostDisplayName)
                        .withAdditionalProperty("tags", List.of())
                        .build()
        );
        emailActionMessage.setEvents(List.of(
                new Event.EventBuilder()
                        .withMetadata(new Metadata.MetadataBuilder().build())
                        .withPayload(
                                new Payload.PayloadBuilder()
                                        .withAdditionalProperty("policy_id", policyId1)
                                        .withAdditionalProperty("policy_name", policyName1)
                                        .withAdditionalProperty("policy_description", "not-used-desc")
                                        .withAdditionalProperty("policy_condition", "not-used-condition")
                                        .build()
                        )
                        .build(),
                new Event.EventBuilder()
                        .withMetadata(new Metadata.MetadataBuilder().build())
                        .withPayload(
                                new Payload.PayloadBuilder()
                                        .withAdditionalProperty("policy_id", policyId2)
                                        .withAdditionalProperty("policy_name", policyName2)
                                        .withAdditionalProperty("policy_description", "not-used-desc")
                                        .withAdditionalProperty("policy_condition", "not-used-condition")
                                        .build()
                        )
                        .build()
        ));

        emailActionMessage.setAccountId(accountId);

        return emailActionMessage;
    }
}
