package com.redhat.cloud.notifications.connector.secrets;

import com.redhat.cloud.notifications.connector.authentication.secrets.SecretsLoader;
import com.redhat.cloud.notifications.connector.authentication.secrets.SourcesClient;
import com.redhat.cloud.notifications.connector.authentication.secrets.SourcesSecret;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.ORG_ID;
import static com.redhat.cloud.notifications.connector.authentication.AuthenticationExchangeProperty.SECRET_ID;
import static com.redhat.cloud.notifications.connector.authentication.AuthenticationExchangeProperty.SECRET_PASSWORD;
import static com.redhat.cloud.notifications.connector.authentication.AuthenticationExchangeProperty.SECRET_USERNAME;
import static org.apache.camel.test.junit5.TestSupport.createExchangeWithBody;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
public class SecretsLoaderTest extends CamelQuarkusTestSupport {

    @Inject
    SecretsLoader secretsLoader;

    @InjectMock
    @RestClient
    SourcesClient sourcesClient;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    void testNoSecretId() {

        Exchange exchange = createExchangeWithBody(context, "");
        exchange.setProperty(ORG_ID, "org-id");
        secretsLoader.process(exchange);

        verify(sourcesClient, never()).getById(anyString(), anyString(), anyLong());
    }

    @Test
    void testWithSecretId() {

        SourcesSecret sourcesSecret = new SourcesSecret();
        sourcesSecret.username = "john_doe";
        sourcesSecret.password = "passw0rd";
        when(sourcesClient.getById(anyString(), anyString(), anyLong())).thenReturn(sourcesSecret);

        Exchange exchange = createExchangeWithBody(context, "");
        exchange.setProperty(ORG_ID, "org-id");
        exchange.setProperty(SECRET_ID, 123L);
        secretsLoader.process(exchange);

        verify(sourcesClient, times(1)).getById(anyString(), anyString(), anyLong());
        assertEquals(sourcesSecret.username, exchange.getProperty(SECRET_USERNAME, String.class));
        assertEquals(sourcesSecret.password, exchange.getProperty(SECRET_PASSWORD, String.class));
    }
}
