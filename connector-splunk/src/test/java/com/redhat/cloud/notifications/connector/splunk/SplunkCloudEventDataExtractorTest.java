package com.redhat.cloud.notifications.connector.splunk;

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
import static com.redhat.cloud.notifications.connector.authentication.AuthenticationExchangeProperty.SECRET_PASSWORD;
import static com.redhat.cloud.notifications.connector.authentication.AuthenticationType.SECRET_TOKEN;
import static com.redhat.cloud.notifications.connector.splunk.ExchangeProperty.ACCOUNT_ID;
import static com.redhat.cloud.notifications.connector.splunk.ExchangeProperty.TARGET_URL_NO_SCHEME;
import static com.redhat.cloud.notifications.connector.splunk.ExchangeProperty.TRUST_ALL;
import static com.redhat.cloud.notifications.connector.splunk.SplunkCloudEventDataExtractor.NOTIF_METADATA;
import static com.redhat.cloud.notifications.connector.splunk.SplunkCloudEventDataExtractor.SERVICES_COLLECTOR_EVENT;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class SplunkCloudEventDataExtractorTest extends CamelQuarkusTestSupport {

    @Inject
    SplunkCloudEventDataExtractor splunkCloudEventDataExtractor;

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
    void testExtractWithWrongTargetUrlPath() throws Exception {
        testExtract("https://foo.bar", true);
    }

    @Test
    void testExtractWithTrailingSlashInTargetUrlPath() throws Exception {
        testExtract("https://foo.bar/", false);
    }

    @Test
    void testExtractWithAnoherWrongTargetUrlPath() throws Exception {
        testExtract("https://foo.bar/services/collector", false);
    }

    @Test
    void testExtractWithValidTargetUrlPath() throws Exception {
        testExtract("https://foo.bar/services/collector/event", false);
    }

    @Test
    void testExtractWithRawTargetUrlPath() throws Exception {
        testExtract("https://foo.bar/services/collector/raw", false);
    }

    private void assertValidTargetUrl(String url) {
        Exchange exchange = createExchangeWithBody("I am not used!");
        JsonObject cloudEventData = createCloudEventData(url, false);
        assertDoesNotThrow(() -> splunkCloudEventDataExtractor.extract(exchange, cloudEventData));
    }

    private void assertInvalidTargetUrl(String url, Class<? extends Exception> expectedException) {
        Exchange exchange = createExchangeWithBody("I am not used!");
        JsonObject cloudEventData = createCloudEventData(url, false);
        assertThrows(expectedException, () -> splunkCloudEventDataExtractor.extract(exchange, cloudEventData));
    }

    private void testExtract(String url, boolean trustAll) throws Exception {
        Exchange exchange = createExchangeWithBody("I am not used!");
        JsonObject cloudEventData = createCloudEventData(url, trustAll);
        /*
         * The 'extract' method will modify 'cloudEventData'.
         * We need to run assertions on the original JsonObject so we're making a copy of it.
         */
        JsonObject cloudEventDataCopy = cloudEventData.copy();
        splunkCloudEventDataExtractor.extract(exchange, cloudEventData);

        assertEquals(cloudEventDataCopy.getString("account_id"), exchange.getProperty(ACCOUNT_ID, String.class));
        assertTrue(exchange.getProperty(TARGET_URL, String.class).endsWith(SERVICES_COLLECTOR_EVENT));
        // Trailing slashes should be removed before we modify the target URL path.
        assertFalse(exchange.getProperty(TARGET_URL, String.class).endsWith("/" + SERVICES_COLLECTOR_EVENT));
        assertTrue(exchange.getProperty(TARGET_URL_NO_SCHEME, String.class).endsWith(SERVICES_COLLECTOR_EVENT));

        JsonObject expectedMetadata = cloudEventDataCopy.getJsonObject(NOTIF_METADATA);
        assertEquals(expectedMetadata.getString("X-Insight-Token"), exchange.getProperty(SECRET_PASSWORD, String.class));
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
        metadata.put("X-Insights-Token", "super-secret-token");
        metadata.put("authentication", authentication);

        JsonObject cloudEventData = new JsonObject();
        cloudEventData.put("org_id", DEFAULT_ORG_ID);
        cloudEventData.put("account_id", DEFAULT_ACCOUNT_ID);
        cloudEventData.put(NOTIF_METADATA, metadata);

        return cloudEventData;
    }
}
