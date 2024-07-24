package com.redhat.cloud.notifications.connector.pagerduty;

import com.redhat.cloud.notifications.connector.authentication.AuthenticationType;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.http.HttpRequest;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.protocol.BasicHttpContext;

import static com.redhat.cloud.notifications.connector.authentication.AuthenticationExchangeProperty.AUTHENTICATION_TYPE;
import static com.redhat.cloud.notifications.connector.authentication.AuthenticationExchangeProperty.SECRET_PASSWORD;

@ApplicationScoped
public class AuthenticationProcessor implements Processor {

    // TODO: INTEGRATION_KEY = "routing_key"
    private static final String USERNAME = "rh_insights_integration";

    @Override
    public void process(Exchange exchange) throws Exception {
        AuthenticationType authType = exchange.getProperty(AUTHENTICATION_TYPE, AuthenticationType.class);
        if (authType != null) {

            String secretPassword = exchange.getProperty(SECRET_PASSWORD, String.class);

            // TODO no HTTP authentication used
            switch (authType) {
                case BASIC -> {
                    throw new IllegalStateException("Unsupported authentication type: BASIC");
                }
                case BEARER -> {
                    throw new IllegalStateException("Unsupported authentication type: BEARER");
                }
                case SECRET_TOKEN -> {
                    if (secretPassword != null) {
                        Credentials credentials = new UsernamePasswordCredentials(USERNAME, secretPassword);
                        HttpRequest httpRequest = new BasicHttpRequest("POST", "/");
                        String headerValue = new BasicScheme()
                                .authenticate(credentials, httpRequest, new BasicHttpContext())
                                .getValue();
                        exchange.getIn().setHeader("Authorization", headerValue);
                    }
                }
                default -> {
                    throw new IllegalStateException("Unexpected authentication type: " + authType);
                }
            }
        }
    }
}
