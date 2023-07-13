package com.redhat.cloud.notifications.connector.servicenow;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.protocol.BasicHttpContext;

import static com.redhat.cloud.notifications.connector.servicenow.ExchangeProperty.AUTHENTICATION_TOKEN;

public class BasicAuthenticationProcessor implements Processor {

    private static final String USERNAME = "rh_insights_integration";

    public void process(Exchange exchange) throws AuthenticationException {

        String token = exchange.getProperty(AUTHENTICATION_TOKEN, String.class);
        if (token == null || token.isBlank()) {
            return;
        }

        Credentials credentials = new UsernamePasswordCredentials(USERNAME, token);
        HttpRequest request = new BasicHttpRequest("POST", "/");

        Header header = new BasicScheme().authenticate(credentials, request, new BasicHttpContext());

        exchange.getIn().setHeader("Authorization", header.getValue());
    }
}
