package com.redhat.cloud.notifications.connector.pagerduty;

import com.redhat.cloud.notifications.connector.authentication.AuthenticationType;
import com.redhat.cloud.notifications.connector.pagerduty.config.PagerDutyConnectorConfig;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Test;

import static com.redhat.cloud.notifications.connector.authentication.AuthenticationExchangeProperty.AUTHENTICATION_TYPE;
import static com.redhat.cloud.notifications.connector.authentication.AuthenticationExchangeProperty.SECRET_ID;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTestUtils.createCloudEventData;
import static org.apache.camel.test.junit5.TestSupport.createExchangeWithBody;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@QuarkusTest
public class PagerDutyCloudEventDataExtractorTest extends CamelQuarkusTestSupport {

    @Inject
    PagerDutyCloudEventDataExtractor pagerDutyCloudEventDataExtractor;

    @InjectMock
    PagerDutyConnectorConfig config;

    @Test
    void testExtractWithValidTargetUrlPath() {
        Exchange exchange = createExchangeWithBody(context, "I am not used!");
        JsonObject cloudEventData = createCloudEventData();
        when(config.getPagerDutyUrl()).thenReturn("https://foo.bar");
        /*
         * The 'extract' method will modify 'cloudEventData'.
         * We need to run assertions on the original JsonObject, so we're making a copy of it.
         */
        JsonObject cloudEventDataCopy = cloudEventData.copy();
        pagerDutyCloudEventDataExtractor.extract(exchange, cloudEventData);

        JsonObject expectedAuthentication = cloudEventDataCopy.getJsonObject("authentication");
        assertEquals(expectedAuthentication.getString("type"), exchange.getProperty(AUTHENTICATION_TYPE, AuthenticationType.class).name());
        assertEquals(expectedAuthentication.getLong("secretId"), exchange.getProperty(SECRET_ID, Long.class));
    }
}
