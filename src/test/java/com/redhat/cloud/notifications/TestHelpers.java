package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.auth.RHIdentityAuthMechanism;
import com.redhat.cloud.notifications.ingress.Action;
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
import java.util.Base64;

import static org.junit.Assert.fail;

public class TestHelpers {
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

    public static String serializeAction(Action action) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonEncoder jsonEncoder = EncoderFactory.get().jsonEncoder(Action.getClassSchema(), baos);
        DatumWriter<Action> writer = new SpecificDatumWriter<>(Action.class);
        writer.write(action, jsonEncoder);
        jsonEncoder.flush();

        return baos.toString(StandardCharsets.UTF_8);
    }
}
