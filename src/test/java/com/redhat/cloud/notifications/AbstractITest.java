package com.redhat.cloud.notifications;

import io.restassured.http.Header;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeAll;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.fail;

public abstract class AbstractITest {

    static Header authHeader;       // User with access rights
    static Header authRbacNoAccess; // Hans Dampf has no rbac access rights
    static Header authHeaderNoAccount; // Account number is empty

    static final String API_BASE_V1_0 = "/api/notifications/v1.0";
    static final String API_BASE_V1 = "/api/notifications/v1";

    @BeforeAll
    static void setupRhId() {
        authHeader = new Header("x-rh-identity", getFileAsString("rhid-examples/rhid.txt"));
        authRbacNoAccess = new Header("x-rh-identity", getFileAsString("rhid-examples/rhid_hans.txt"));
        authHeaderNoAccount = new Header("x-rh-identity", getFileAsString("rhid-examples/rhid_no_account.txt"));
    }

    public static String getFileAsString(String filename) {
        try {
            InputStream is = AbstractITest.class.getClassLoader().getResourceAsStream(filename);
            return IOUtils.toString(is, StandardCharsets.UTF_8);
        } catch (Exception e) {
            fail("Failed to read rhid example file: " + e.getMessage());
            return "";
        }
    }
}
