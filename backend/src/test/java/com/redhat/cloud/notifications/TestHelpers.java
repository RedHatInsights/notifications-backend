package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Encoder;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import io.restassured.http.Header;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.redhat.cloud.notifications.Constants.X_RH_IDENTITY_HEADER;

public class TestHelpers {

    public static final String policyId1 = "abcd-efghi-jkl-lmn";
    public static final String policyName1 = "Foobar";
    public static final String policyId2 = "0123-456-789-5721f";
    public static final String policyName2 = "Latest foo is installed";
    public static final String eventType = "test-email-subscription-instant";

    public static final Encoder encoder = new Encoder();

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
        return encoder.encode(action);
    }

    public static Action createPoliciesAction(String accountId, String bundle, String application, String hostDisplayName) {
        Action emailActionMessage = new Action();
        emailActionMessage.setBundle(bundle);
        emailActionMessage.setApplication(application);
        emailActionMessage.setTimestamp(LocalDateTime.of(2020, 10, 3, 15, 22, 13, 25));
        emailActionMessage.setEventType(eventType);
        emailActionMessage.setRecipients(List.of());

        emailActionMessage.setContext(Map.of(
                "inventory_id", "host-01",
                "system_check_in", "2020-08-03T15:22:42.199046",
                "display_name", hostDisplayName,
                "tags", List.of()
        ));
        emailActionMessage.setEvents(List.of(
                Event
                        .newBuilder()
                        .setMetadataBuilder(Metadata.newBuilder())
                        .setPayload(Map.of(
                                "policy_id", policyId1,
                                "policy_name", policyName1,
                                "policy_description", "not-used-desc",
                                "policy_condition", "not-used-condition"
                        ))
                        .build(),
                Event
                        .newBuilder()
                        .setMetadataBuilder(Metadata.newBuilder())
                        .setPayload(Map.of(
                                "policy_id", policyId2,
                                "policy_name", policyName2,
                                "policy_description", "not-used-desc",
                                "policy_condition", "not-used-condition"
                        ))
                        .build()
        ));

        emailActionMessage.setAccountId(accountId);

        return emailActionMessage;
    }
}
