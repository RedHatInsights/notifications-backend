package com.redhat.cloud.notifications.connector.http;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.mutiny.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

/**
 * HTTP client service that replaces Camel HTTP component functionality.
 * Uses Vert.x WebClient for reactive HTTP operations.
 */
@ApplicationScoped
public class HttpClientService {

    @Inject
    Vertx vertx;

    @Inject
    HttpConnectorConfig connectorConfig;

    /**
     * Send an HTTP POST request with JSON payload.
     * Replaces Camel's HTTP endpoint functionality.
     */
    public Uni<HttpResponse> sendPost(String url, JsonObject payload, Map<String, String> headers, boolean trustAll) {
        return sendRequest("POST", url, payload, headers, trustAll);
    }

    /**
     * Send an HTTP GET request.
     */
    public Uni<HttpResponse> sendGet(String url, Map<String, String> headers, boolean trustAll) {
        return sendRequest("GET", url, null, headers, trustAll);
    }

    /**
     * Send an HTTP PUT request with JSON payload.
     */
    public Uni<HttpResponse> sendPut(String url, JsonObject payload, Map<String, String> headers, boolean trustAll) {
        return sendRequest("PUT", url, payload, headers, trustAll);
    }

    private Uni<HttpResponse> sendRequest(String method, String url, JsonObject payload, Map<String, String> headers, boolean trustAll) {
        try {
            URI uri = new URI(url);

            WebClientOptions options = new WebClientOptions()
                    .setDefaultPort(uri.getPort() != -1 ? uri.getPort() : (uri.getScheme().equals("https") ? 443 : 80))
                    .setDefaultHost(uri.getHost())
                    .setSsl(uri.getScheme().equals("https"))
                    .setConnectTimeout(connectorConfig.getHttpConnectTimeout())
                    .setIdleTimeout((int) Duration.ofMillis(connectorConfig.getHttpSocketTimeout()).getSeconds())
                    .setTrustAll(trustAll);

            if (trustAll) {
                options.setTrustAll(true)
                       .setVerifyHost(false);
            }

            WebClient client = WebClient.create(vertx.getDelegate(), options);

            io.vertx.ext.web.client.HttpRequest<io.vertx.core.buffer.Buffer> request = client.request(
                    io.vertx.core.http.HttpMethod.valueOf(method),
                    uri.getPath() + (uri.getQuery() != null ? "?" + uri.getQuery() : "")
            );

            // Add headers
            if (headers != null) {
                headers.forEach(request::putHeader);
            }

            // Set content type for requests with payload
            if (payload != null) {
                request.putHeader("Content-Type", "application/json");
            }

            Log.debugf("Sending %s request to %s", method, url);

            Uni<io.vertx.ext.web.client.HttpResponse<io.vertx.core.buffer.Buffer>> responseUni;

            if (payload != null) {
                responseUni = Uni.createFrom().completionStage(request.sendJson(payload.getMap()).toCompletionStage());
            } else {
                responseUni = Uni.createFrom().completionStage(request.send().toCompletionStage());
            }

            return responseUni
                    .onItem().transform(response -> {
                        HttpResponse httpResponse = new HttpResponse(
                                response.statusCode(),
                                response.bodyAsString(),
                                response.headers().entries()
                        );

                        Log.debugf("Received response: status=%d, body=%s",
                                  response.statusCode(),
                                  response.bodyAsString());

                        return httpResponse;
                    })
                    .onFailure().invoke(failure ->
                            Log.errorf(failure, "HTTP request failed: %s %s", method, url)
                    );

        } catch (Exception e) {
            Log.errorf(e, "Failed to create HTTP request: %s %s", method, url);
            return Uni.createFrom().failure(e);
        }
    }

    /**
     * HTTP response wrapper
     */
    public static class HttpResponse {
        private final int statusCode;
        private final String body;
        private final java.util.List<java.util.Map.Entry<String, String>> headers;

        public HttpResponse(int statusCode, String body, java.util.List<java.util.Map.Entry<String, String>> headers) {
            this.statusCode = statusCode;
            this.body = body;
            this.headers = headers;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getBody() {
            return body;
        }

        public java.util.List<java.util.Map.Entry<String, String>> getHeaders() {
            return headers;
        }

        public boolean isSuccessful() {
            return statusCode >= 200 && statusCode < 300;
        }

        public boolean isClientError() {
            return statusCode >= 400 && statusCode < 500;
        }

        public boolean isServerError() {
            return statusCode >= 500;
        }
    }
}
