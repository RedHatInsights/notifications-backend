package com.redhat.cloud.notifications.connector.servicenow;

import com.redhat.cloud.notifications.connector.TestLifecycleManager;
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
import static com.redhat.cloud.notifications.connector.servicenow.ExchangeProperty.ACCOUNT_ID;
import static com.redhat.cloud.notifications.connector.servicenow.ExchangeProperty.AUTHENTICATION_TOKEN;
import static com.redhat.cloud.notifications.connector.servicenow.ExchangeProperty.TARGET_URL_NO_SCHEME;
import static com.redhat.cloud.notifications.connector.servicenow.ExchangeProperty.TRUST_ALL;
import static com.redhat.cloud.notifications.connector.servicenow.ServiceNowCloudEventDataExtractor.NOTIF_METADATA;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class ServiceNowCloudEventDataExtractorTest extends CamelQuarkusTestSupport {

    @Inject
    ServiceNowCloudEventDataExtractor serviceNowCloudEventDataExtractor;

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
         * We need to run assertions on the original JsonObject so we're making a copy of it.
         */
        JsonObject cloudEventDataCopy = cloudEventData.copy();
        serviceNowCloudEventDataExtractor.extract(exchange, cloudEventData);

        assertEquals(cloudEventDataCopy.getString("account_id"), exchange.getProperty(ACCOUNT_ID, String.class));
        assertEquals(cloudEventDataCopy.getJsonObject(NOTIF_METADATA).getString("url"), exchange.getProperty(TARGET_URL, String.class));
        assertTrue(cloudEventDataCopy.getJsonObject(NOTIF_METADATA).getString("url").endsWith(exchange.getProperty(TARGET_URL_NO_SCHEME, String.class)));
        assertEquals(cloudEventDataCopy.getJsonObject(NOTIF_METADATA).getString("X-Insight-Token"), exchange.getProperty(AUTHENTICATION_TOKEN, String.class));
        assertEquals(cloudEventDataCopy.getJsonObject(NOTIF_METADATA).getString("trustAll"), exchange.getProperty(TRUST_ALL, Boolean.class).toString());
    }

    private JsonObject createCloudEventData(String url, boolean trustAll) {
        JsonObject metadata = new JsonObject();
        metadata.put("url", url);
        metadata.put("trustAll", Boolean.toString(trustAll));
        metadata.put("X-Insights-Token", "super-secret-token");

        JsonObject cloudEventData = new JsonObject();
        cloudEventData.put("org_id", DEFAULT_ORG_ID);
        cloudEventData.put("account_id", DEFAULT_ACCOUNT_ID);
        cloudEventData.put(NOTIF_METADATA, metadata);

        return cloudEventData;
    }
}
