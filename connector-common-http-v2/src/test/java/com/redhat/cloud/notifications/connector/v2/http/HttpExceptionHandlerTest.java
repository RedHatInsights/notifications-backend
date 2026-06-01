package com.redhat.cloud.notifications.connector.v2.http;

import com.redhat.cloud.notifications.connector.v2.BaseConnectorIntegrationTest;
import com.redhat.cloud.notifications.connector.v2.http.models.HandledHttpExceptionDetails;
import io.netty.channel.ConnectTimeoutException;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.ce.IncomingCloudEventMetadata;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.ClientWebApplicationException;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import static com.redhat.cloud.notifications.connector.v2.http.HttpErrorType.CONNECTION_REFUSED;
import static com.redhat.cloud.notifications.connector.v2.http.HttpErrorType.CONNECT_TIMEOUT;
import static com.redhat.cloud.notifications.connector.v2.http.HttpErrorType.HTTP_3XX;
import static com.redhat.cloud.notifications.connector.v2.http.HttpErrorType.HTTP_4XX;
import static com.redhat.cloud.notifications.connector.v2.http.HttpErrorType.HTTP_5XX;
import static com.redhat.cloud.notifications.connector.v2.http.HttpErrorType.SOCKET_TIMEOUT;
import static com.redhat.cloud.notifications.connector.v2.http.HttpErrorType.SSL_HANDSHAKE;
import static com.redhat.cloud.notifications.connector.v2.http.HttpErrorType.UNKNOWN_HOST;
import static com.redhat.cloud.notifications.connector.v2.http.HttpErrorType.UNSUPPORTED_SSL_MESSAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
class HttpExceptionHandlerTest {

    @Inject
    HttpExceptionHandler httpExceptionHandler;

    @Test
    void testProcess3xxError() {
        IncomingCloudEventMetadata<JsonObject> incomingCloudEvent = buildIncomingCloudEvent("https://example.com/webhook");

        Response response = Response.status(301).entity("Moved Permanently").build();
        ClientWebApplicationException exception = new ClientWebApplicationException(response);

        HandledHttpExceptionDetails result = (HandledHttpExceptionDetails) httpExceptionHandler.process(exception, incomingCloudEvent);

        assertNotNull(result);
        assertEquals(HTTP_3XX, result.httpErrorType);
        assertEquals(301, result.httpStatusCode);
        assertEquals("https://example.com/webhook", result.targetUrl);
        assertEquals("Moved Permanently", result.responseBody);
    }

    @Test
    void testProcess4xxError() {
        IncomingCloudEventMetadata<JsonObject> incomingCloudEvent = buildIncomingCloudEvent("https://example.com/webhook");

        Response response = Response.status(404).entity("Not Found").build();
        ClientWebApplicationException exception = new ClientWebApplicationException(response);

        HandledHttpExceptionDetails result = (HandledHttpExceptionDetails) httpExceptionHandler.process(exception, incomingCloudEvent);

        assertNotNull(result);
        assertEquals(HTTP_4XX, result.httpErrorType);
        assertEquals(404, result.httpStatusCode);
        assertEquals("https://example.com/webhook", result.targetUrl);
        assertEquals("Not Found", result.responseBody);
    }

    @Test
    void testProcess5xxError() {
        IncomingCloudEventMetadata<JsonObject> incomingCloudEvent = buildIncomingCloudEvent("https://example.com/webhook");

        Response response = Response.status(500).entity("Internal Server Error").build();
        ClientWebApplicationException exception = new ClientWebApplicationException(response);

        HandledHttpExceptionDetails result = (HandledHttpExceptionDetails) httpExceptionHandler.process(exception, incomingCloudEvent);

        assertNotNull(result);
        assertEquals(HTTP_5XX, result.httpErrorType);
        assertEquals(500, result.httpStatusCode);
        assertEquals("https://example.com/webhook", result.targetUrl);
        assertEquals("Internal Server Error", result.responseBody);
    }

    @Test
    void testProcessTooManyRequestsError() {
        IncomingCloudEventMetadata<JsonObject> incomingCloudEvent = buildIncomingCloudEvent("https://example.com/webhook");

        Response response = Response.status(429).entity("Too Many Requests").build();
        ClientWebApplicationException exception = new ClientWebApplicationException(response);

        HandledHttpExceptionDetails result = (HandledHttpExceptionDetails) httpExceptionHandler.process(exception, incomingCloudEvent);

        assertNotNull(result);
        assertEquals(HTTP_5XX, result.httpErrorType);
        assertEquals(429, result.httpStatusCode);
        assertEquals("https://example.com/webhook", result.targetUrl);
        assertEquals("Too Many Requests", result.responseBody);
    }

    @Test
    void testProcessSocketTimeoutException() {
        IncomingCloudEventMetadata<JsonObject> incomingCloudEvent = buildIncomingCloudEvent("https://example.com/webhook");
        SocketTimeoutException exception = new SocketTimeoutException("Connection timed out");

        HandledHttpExceptionDetails result = (HandledHttpExceptionDetails) httpExceptionHandler.process(exception, incomingCloudEvent);

        assertNotNull(result);
        assertEquals(SOCKET_TIMEOUT, result.httpErrorType);
        assertEquals("https://example.com/webhook", result.targetUrl);
        assertNull(result.httpStatusCode);
        assertNull(result.responseBody);
    }

    @Test
    void testProcessSSLHandshakeException() {
        IncomingCloudEventMetadata<JsonObject> incomingCloudEvent = buildIncomingCloudEvent("https://example.com/webhook");
        SSLHandshakeException exception = new SSLHandshakeException("SSL handshake failed");

        HandledHttpExceptionDetails result = (HandledHttpExceptionDetails) httpExceptionHandler.process(exception, incomingCloudEvent);

        assertNotNull(result);
        assertEquals(SSL_HANDSHAKE, result.httpErrorType);
        assertEquals("https://example.com/webhook", result.targetUrl);
        assertNull(result.httpStatusCode);
    }

    @Test
    void testProcessUnknownHostException() {
        IncomingCloudEventMetadata<JsonObject> incomingCloudEvent = buildIncomingCloudEvent("https://example.com/webhook");
        UnknownHostException exception = new UnknownHostException("unknown.host.example.com");

        HandledHttpExceptionDetails result = (HandledHttpExceptionDetails) httpExceptionHandler.process(exception, incomingCloudEvent);

        assertNotNull(result);
        assertEquals(UNKNOWN_HOST, result.httpErrorType);
        assertEquals("https://example.com/webhook", result.targetUrl);
        assertNull(result.httpStatusCode);
    }

    @Test
    void testProcessSSLException() {
        IncomingCloudEventMetadata<JsonObject> incomingCloudEvent = buildIncomingCloudEvent("https://example.com/webhook");
        SSLException exception = new SSLException("Unsupported SSL message");

        HandledHttpExceptionDetails result = (HandledHttpExceptionDetails) httpExceptionHandler.process(exception, incomingCloudEvent);

        assertNotNull(result);
        assertEquals(UNSUPPORTED_SSL_MESSAGE, result.httpErrorType);
        assertEquals("https://example.com/webhook", result.targetUrl);
        assertNull(result.httpStatusCode);
    }

    @Test
    void testProcessOtherException() {
        IncomingCloudEventMetadata<JsonObject> incomingCloudEvent = buildIncomingCloudEvent("https://example.com/webhook");
        RuntimeException exception = new RuntimeException("Some other error");

        HandledHttpExceptionDetails result = (HandledHttpExceptionDetails) httpExceptionHandler.process(exception, incomingCloudEvent);

        assertNotNull(result);
        assertNull(result.httpErrorType);
        assertEquals("https://example.com/webhook", result.targetUrl);
        assertNull(result.httpStatusCode);
    }

    @Test
    void testProcessWithNullTargetUrl() {
        JsonObject payload = new JsonObject()
            .put("org_id", "12345")
            .put("endpoint_properties", new JsonObject())
            .put("payload", new JsonObject().put("test", "data"));

        IncomingCloudEventMetadata<JsonObject> incomingCloudEvent = BaseConnectorIntegrationTest.buildIncomingCloudEvent(
            "test-event-id",
            "com.redhat.console.notification.toCamel.http",
            payload
        );

        SocketTimeoutException exception = new SocketTimeoutException("Connection timed out");

        HandledHttpExceptionDetails result = (HandledHttpExceptionDetails) httpExceptionHandler.process(exception, incomingCloudEvent);

        assertNotNull(result);
        assertEquals(SOCKET_TIMEOUT, result.httpErrorType);
        assertNull(result.targetUrl);
    }

    @Test
    void testProcessWithMissingEndpointProperties() {
        JsonObject payload = new JsonObject()
            .put("org_id", "12345")
            .put("payload", new JsonObject().put("test", "data"));

        IncomingCloudEventMetadata<JsonObject> incomingCloudEvent = BaseConnectorIntegrationTest.buildIncomingCloudEvent(
            "test-event-id",
            "com.redhat.console.notification.toCamel.http",
            payload
        );

        SocketTimeoutException exception = new SocketTimeoutException("Connection timed out");

        HandledHttpExceptionDetails result = (HandledHttpExceptionDetails) httpExceptionHandler.process(exception, incomingCloudEvent);

        assertNotNull(result);
        assertEquals(SOCKET_TIMEOUT, result.httpErrorType);
        assertNull(result.targetUrl);
    }

    @Test
    void testProcessUnexpectedStatusCode() {
        IncomingCloudEventMetadata<JsonObject> incomingCloudEvent = buildIncomingCloudEvent("https://example.com/webhook");

        Response response = Response.status(200).entity("OK").build();
        ClientWebApplicationException exception = new ClientWebApplicationException(response);

        HandledHttpExceptionDetails result = (HandledHttpExceptionDetails) httpExceptionHandler.process(exception, incomingCloudEvent);

        assertNotNull(result);
        assertNull(result.httpErrorType);
        assertEquals(200, result.httpStatusCode);
        assertEquals("https://example.com/webhook", result.targetUrl);
    }

    @Test
    void testProcessConnectTimeoutException() {
        IncomingCloudEventMetadata<JsonObject> incomingCloudEvent = buildIncomingCloudEvent("https://example.com/webhook");
        ConnectTimeoutException exception = new ConnectTimeoutException("connection timed out");

        HandledHttpExceptionDetails result = (HandledHttpExceptionDetails) httpExceptionHandler.process(exception, incomingCloudEvent);

        assertNotNull(result);
        assertEquals(CONNECT_TIMEOUT, result.httpErrorType);
        assertEquals("https://example.com/webhook", result.targetUrl);
        assertNull(result.httpStatusCode);
    }

    @Test
    void testProcessConnectException() {
        IncomingCloudEventMetadata<JsonObject> incomingCloudEvent = buildIncomingCloudEvent("https://example.com/webhook");
        ConnectException exception = new ConnectException("Connection refused");

        HandledHttpExceptionDetails result = (HandledHttpExceptionDetails) httpExceptionHandler.process(exception, incomingCloudEvent);

        assertNotNull(result);
        assertEquals(CONNECTION_REFUSED, result.httpErrorType);
        assertEquals("https://example.com/webhook", result.targetUrl);
        assertNull(result.httpStatusCode);
    }

    @Test
    void testProcessWrappedUnknownHostException() {
        IncomingCloudEventMetadata<JsonObject> incomingCloudEvent = buildIncomingCloudEvent("https://example.com/webhook");
        ProcessingException exception = new ProcessingException(new UnknownHostException("unknown.host.example.com"));

        HandledHttpExceptionDetails result = (HandledHttpExceptionDetails) httpExceptionHandler.process(exception, incomingCloudEvent);

        assertNotNull(result);
        assertEquals(UNKNOWN_HOST, result.httpErrorType);
        assertEquals("https://example.com/webhook", result.targetUrl);
    }

    @Test
    void testProcessWrappedSSLHandshakeException() {
        IncomingCloudEventMetadata<JsonObject> incomingCloudEvent = buildIncomingCloudEvent("https://example.com/webhook");
        ProcessingException exception = new ProcessingException(new SSLHandshakeException("SSL handshake failed"));

        HandledHttpExceptionDetails result = (HandledHttpExceptionDetails) httpExceptionHandler.process(exception, incomingCloudEvent);

        assertNotNull(result);
        assertEquals(SSL_HANDSHAKE, result.httpErrorType);
        assertEquals("https://example.com/webhook", result.targetUrl);
    }

    @Test
    void testProcessWrappedSocketTimeoutException() {
        IncomingCloudEventMetadata<JsonObject> incomingCloudEvent = buildIncomingCloudEvent("https://example.com/webhook");
        ProcessingException exception = new ProcessingException(new SocketTimeoutException("Read timed out"));

        HandledHttpExceptionDetails result = (HandledHttpExceptionDetails) httpExceptionHandler.process(exception, incomingCloudEvent);

        assertNotNull(result);
        assertEquals(SOCKET_TIMEOUT, result.httpErrorType);
        assertEquals("https://example.com/webhook", result.targetUrl);
    }

    @Test
    void testProcessWrappedConnectTimeoutException() {
        IncomingCloudEventMetadata<JsonObject> incomingCloudEvent = buildIncomingCloudEvent("https://example.com/webhook");
        ProcessingException exception = new ProcessingException(new ConnectTimeoutException("connection timed out"));

        HandledHttpExceptionDetails result = (HandledHttpExceptionDetails) httpExceptionHandler.process(exception, incomingCloudEvent);

        assertNotNull(result);
        assertEquals(CONNECT_TIMEOUT, result.httpErrorType);
        assertEquals("https://example.com/webhook", result.targetUrl);
    }

    @Test
    void testProcessWrappedConnectException() {
        IncomingCloudEventMetadata<JsonObject> incomingCloudEvent = buildIncomingCloudEvent("https://example.com/webhook");
        ProcessingException exception = new ProcessingException(new ConnectException("Connection refused"));

        HandledHttpExceptionDetails result = (HandledHttpExceptionDetails) httpExceptionHandler.process(exception, incomingCloudEvent);

        assertNotNull(result);
        assertEquals(CONNECTION_REFUSED, result.httpErrorType);
        assertEquals("https://example.com/webhook", result.targetUrl);
    }

    @Test
    void testResponseBodyTruncatedTo1000Chars() {
        IncomingCloudEventMetadata<JsonObject> incomingCloudEvent = buildIncomingCloudEvent("https://example.com/webhook");

        String longBody = "x".repeat(2000);
        Response response = Response.status(500).entity(longBody).build();
        ClientWebApplicationException exception = new ClientWebApplicationException(response);

        HandledHttpExceptionDetails result = (HandledHttpExceptionDetails) httpExceptionHandler.process(exception, incomingCloudEvent);

        assertNotNull(result);
        assertEquals(1000, result.responseBody.length());
        assertEquals("x".repeat(1000), result.responseBody);
    }

    @Test
    void testResponseBodyReadFailureHandledGracefully() {
        IncomingCloudEventMetadata<JsonObject> incomingCloudEvent = buildIncomingCloudEvent("https://example.com/webhook");

        Response response = Response.status(500).build();
        ClientWebApplicationException exception = new ClientWebApplicationException(response);

        HandledHttpExceptionDetails result = (HandledHttpExceptionDetails) httpExceptionHandler.process(exception, incomingCloudEvent);

        assertNotNull(result);
        assertEquals(HTTP_5XX, result.httpErrorType);
        assertEquals(500, result.httpStatusCode);
        assertNull(result.responseBody);
    }

    @Test
    void testProcessNestedProcessingException() {
        IncomingCloudEventMetadata<JsonObject> incomingCloudEvent = buildIncomingCloudEvent("https://example.com/webhook");
        ProcessingException exception = new ProcessingException(new ProcessingException(new UnknownHostException("unknown.host.example.com")));

        HandledHttpExceptionDetails result = (HandledHttpExceptionDetails) httpExceptionHandler.process(exception, incomingCloudEvent);

        assertNotNull(result);
        assertEquals(UNKNOWN_HOST, result.httpErrorType);
        assertEquals("https://example.com/webhook", result.targetUrl);
    }

    private IncomingCloudEventMetadata<JsonObject> buildIncomingCloudEvent(String targetUrl) {
        JsonObject payload = new JsonObject()
            .put("org_id", "12345")
            .put("endpoint_properties", new JsonObject().put("url", targetUrl))
            .put("payload", new JsonObject().put("test", "data"));

        return BaseConnectorIntegrationTest.buildIncomingCloudEvent(
            "test-event-id",
            "com.redhat.console.notification.toCamel.http",
            payload
        );
    }
}
