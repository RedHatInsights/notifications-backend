package com.redhat.cloud.notifications.connector.pagerduty;

import com.redhat.cloud.notifications.connector.TestLifecycleManager;
import com.redhat.cloud.notifications.connector.authentication.AuthenticationType;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.apache.http.ProtocolException;
import org.junit.jupiter.api.Test;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.TARGET_URL;
import static com.redhat.cloud.notifications.connector.authentication.AuthenticationExchangeProperty.AUTHENTICATION_TYPE;
import static com.redhat.cloud.notifications.connector.authentication.AuthenticationExchangeProperty.SECRET_ID;
import static com.redhat.cloud.notifications.connector.authentication.AuthenticationType.SECRET_TOKEN;
import static com.redhat.cloud.notifications.connector.pagerduty.ExchangeProperty.ACCOUNT_ID;
import static com.redhat.cloud.notifications.connector.pagerduty.ExchangeProperty.TARGET_URL_NO_SCHEME;
import static com.redhat.cloud.notifications.connector.pagerduty.ExchangeProperty.TRUST_ALL;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// TODO: rewrite for PagerDuty
@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class PagerDutyCloudEventDataExtractorTest extends CamelQuarkusTestSupport {

    @Inject
    PagerDutyCloudEventDataExtractor serviceNowCloudEventDataExtractor;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    void httpsProtocolShouldBeValid() {
        assertValidTargetUrl("https://example.com/foo?bar=baz");
    }

    @Test
    void httpsProtocolWithPortShouldBeValid() {
        assertValidTargetUrl("https://example.com:8000/foo?bar=baz");
    }

    @Test
    void httpsProtocolWithLocalhostShouldBeValid() {
        assertValidTargetUrl("https://localhost/");
    }

    @Test
    void httpsProtocolWithLocalhostAndPortShouldBeValid() {
        assertValidTargetUrl("https://localhost:1234/");
    }

    @Test
    void httpsProtocolWithIpAddressShouldBeValid() {
        assertValidTargetUrl("https://123.123.123.123/");
    }

    @Test
    void httpsProtocolWithIpAddressAndPortShouldBeValid() {
        assertValidTargetUrl("https://123.123.123.123:1234/");
    }

    @Test
    void httpProtocolShouldBeInvalid() {
        assertInvalidTargetUrl("http://example.com/foo?bar=baz", ProtocolException.class);
    }

    @Test
    void ftpProtocolShouldBeInvalid() {
        assertInvalidTargetUrl("ftp://example.com/foo?bar=baz", IllegalArgumentException.class);
    }

    @Test
    void nonSenseUrlShouldBeInvalid() {
        assertInvalidTargetUrl("foo-bar_baz", IllegalArgumentException.class);
    }

    @Test
    void nullUrlShouldBeInvalid() {
        assertInvalidTargetUrl(null, IllegalArgumentException.class);
    }

    @Test
    void emptyUrlShouldBeInvalid() {
        assertInvalidTargetUrl("", IllegalArgumentException.class);
    }

    @Test
    void testExtractWithValidTargetUrlPath() throws Exception {
        testExtract("https://foo.bar", true);
    }

    private void assertValidTargetUrl(String url) {
        Exchange exchange = createExchangeWithBody("I am not used!");
        JsonObject cloudEventData = createCloudEventData(url, false);
        assertDoesNotThrow(() -> serviceNowCloudEventDataExtractor.extract(exchange, cloudEventData));
    }

    private void assertInvalidTargetUrl(String url, Class<? extends Exception> expectedException) {
        Exchange exchange = createExchangeWithBody("I am not used!");
        JsonObject cloudEventData = createCloudEventData(url, false);
        assertThrows(expectedException, () -> serviceNowCloudEventDataExtractor.extract(exchange, cloudEventData));
    }

    private void testExtract(String url, boolean trustAll) throws Exception {
        Exchange exchange = createExchangeWithBody("I am not used!");
        JsonObject cloudEventData = createCloudEventData(url, trustAll);
        /*
         * The 'extract' method will modify 'cloudEventData'.
         * We need to run assertions on the original JsonObject, so we're making a copy of it.
         */
        JsonObject cloudEventDataCopy = cloudEventData.copy();
        serviceNowCloudEventDataExtractor.extract(exchange, cloudEventData);

        assertEquals(cloudEventDataCopy.getString("account_id"), exchange.getProperty(ACCOUNT_ID, String.class));

        JsonObject expectedMetadata = cloudEventDataCopy.getJsonObject(PagerDutyCloudEventDataExtractor.NOTIF_METADATA);
        assertEquals(expectedMetadata.getString("url"), exchange.getProperty(TARGET_URL, String.class));
        assertTrue(expectedMetadata.getString("url").endsWith(exchange.getProperty(TARGET_URL_NO_SCHEME, String.class)));
        assertEquals(expectedMetadata.getString("trustAll"), exchange.getProperty(TRUST_ALL, Boolean.class).toString());

        JsonObject expectedAuthentication = expectedMetadata.getJsonObject("authentication");
        assertEquals(expectedAuthentication.getString("type"), exchange.getProperty(AUTHENTICATION_TYPE, AuthenticationType.class).name());
        assertEquals(expectedAuthentication.getLong("secretId"), exchange.getProperty(SECRET_ID, Long.class));
    }

    private JsonObject createCloudEventData(String url, boolean trustAll) {
        JsonObject authentication = new JsonObject();
        authentication.put("type", SECRET_TOKEN);
        authentication.put("secretId", 123L);

        JsonObject metadata = new JsonObject();
        metadata.put("url", url);
        metadata.put("trustAll", Boolean.toString(trustAll));
        metadata.put("authentication", authentication);

        JsonObject cloudEventData = new JsonObject();
        cloudEventData.put("org_id", DEFAULT_ORG_ID);
        cloudEventData.put("account_id", DEFAULT_ACCOUNT_ID);
        cloudEventData.put(PagerDutyCloudEventDataExtractor.NOTIF_METADATA, metadata);

        return cloudEventData;
    }
}
