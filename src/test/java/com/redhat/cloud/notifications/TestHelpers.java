package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.auth.RHIdentityAuthMechanism;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.transformers.BaseTransformer;
import io.restassured.http.Header;
import io.vertx.core.json.JsonObject;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.io.JsonEncoder;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.fail;

public class TestHelpers {

    public static BaseTransformer baseTransformer = new BaseTransformer();
    public static final String policyId1 = "abcd-efghi-jkl-lmn";
    public static final String policyName1 = "Foobar";
    public static final String policyId2 = "0123-456-789-5721f";
    public static final String policyName2 = "Latest foo is installed";

    public static String encodeIdentityInfo(String tenant, String username) {
        JsonObject identity = new JsonObject();
        JsonObject user = new JsonObject();
        user.put("username", username);
        identity.put("account_number", tenant);
        identity.put("user", user);
        JsonObject header = new JsonObject();
        header.put("identity", identity);

        String xRhEncoded = null;
        try {
            xRhEncoded = new String(Base64.getEncoder().encode(header.encode().getBytes("UTF-8")));
        } catch (UnsupportedEncodingException e) {
            fail();
        }
        return xRhEncoded;
    }

    public static Header createIdentityHeader(String tenant, String username) {
        return new Header(RHIdentityAuthMechanism.IDENTITY_HEADER, encodeIdentityInfo(tenant, username));
    }

    public static Header createIdentityHeader(String encodedIdentityHeader) {
        return new Header(RHIdentityAuthMechanism.IDENTITY_HEADER, encodedIdentityHeader);
    }

    public static String getFileAsString(String filename) {
        try {
            InputStream is = TestHelpers.class.getClassLoader().getResourceAsStream(filename);
            return IOUtils.toString(is, StandardCharsets.UTF_8);
        } catch (Exception e) {
            fail("Failed to read rhid example file: " + e.getMessage());
            return "";
        }
    }

    public static EmailAggregation createEmailAggregation(String tenant, String bundle, String application, String policyId, String insightsId) {
        EmailAggregation aggregation = new EmailAggregation();
        aggregation.setBundleName(bundle);
        aggregation.setApplicationName(application);
        aggregation.setAccountId(tenant);

        Action emailActionMessage = new Action();
        emailActionMessage.setBundle(bundle);
        emailActionMessage.setApplication(application);
        emailActionMessage.setTimestamp(LocalDateTime.now());
        emailActionMessage.setEventType("testEmailSubscriptionInstant");

        emailActionMessage.setContext(Map.of(
                "insights_id", insightsId,
                "system_check_in", "2020-08-03T15:22:42.199046",
                "display_name", "My test machine",
                "tags", List.of()
        ));
        emailActionMessage.setEvents(List.of(
                Event
                        .newBuilder()
                        .setMetadataBuilder(Metadata.newBuilder())
                        .setPayload(Map.of(
                                "policy_id", policyId,
                                "policy_name", "not-tested-name",
                                "policy_description", "not-used-desc",
                                "policy_condition", "not-used-condition"
                        ))
                        .build()
        ));

        emailActionMessage.setAccountId(tenant);

        JsonObject payload = baseTransformer.transform(emailActionMessage).await().indefinitely();
        aggregation.setPayload(payload);

        return aggregation;
    }

    public static String serializeAction(Action action) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonEncoder jsonEncoder = EncoderFactory.get().jsonEncoder(Action.getClassSchema(), baos);
        DatumWriter<Action> writer = new SpecificDatumWriter<>(Action.class);
        writer.write(action, jsonEncoder);
        jsonEncoder.flush();

        return baos.toString(StandardCharsets.UTF_8);
    }

    public static Action createPoliciesAction(String accountId, String bundle, String application, String hostDisplayName) {
        Action emailActionMessage = new Action();
        emailActionMessage.setBundle(bundle);
        emailActionMessage.setApplication(application);
        emailActionMessage.setTimestamp(LocalDateTime.of(2020, 10, 3, 15, 22, 13, 25));
        emailActionMessage.setEventType("testEmailSubscriptionInstant");

        emailActionMessage.setContext(Map.of(
                "insights_id", "host-01",
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
