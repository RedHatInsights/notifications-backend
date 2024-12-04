package com.redhat.cloud.notifications.auth.rbac;

import io.quarkus.logging.Log;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientResponseContext;
import jakarta.ws.rs.client.ClientResponseFilter;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Filter to look at the response (code) from the Rbac server.
 * Log a warning if we have trouble reaching the server.
 */
public class RbacClientResponseFilter implements ClientResponseFilter {

    @Override
    public void filter(final ClientRequestContext requestContext, final ClientResponseContext responseContext) throws IOException {
        final int responseStatusCode = responseContext.getStatusInfo().getStatusCode();
        if (Response.Status.Family.familyOf(responseStatusCode) == Response.Status.Family.SUCCESSFUL) {
            return;
        }

        Log.warnf("[response_status_code: %s] RBAC responded with a non 2xx status code", responseStatusCode);

        // When the logging level is set to TRACE or lower for this class, log
        // both the request's and the response's details.
        final Logger logger = Logger.getLogger(this.getClass().getName());
        final Level logLevel = logger.getLevel();
        if (Level.FINEST.equals(logLevel) || Level.FINER.equals(logLevel)) {
            Log.tracef(
                "[request_method: %s][request_uri: %s][request_headers: %s] Sent RBAC request",
                requestContext.getMethod(),
                requestContext.getUri(),
                String.format("{%s}", this.headersToString(requestContext.getStringHeaders())),
                responseStatusCode
            );

            Log.tracef(
                "[response_headers: %s][response_status_code: %s][response_body: %s] Received RBAC response",
                this.headersToString(responseContext.getHeaders()),
                responseStatusCode,
                new String(responseContext.getEntityStream().readAllBytes(), StandardCharsets.UTF_8)
            );
        }
    }

    /**
     * Transforms the incoming headers to a human-readable format.
     * @param headers the incoming headers to transform.
     * @return a string containing the headers with the following format:
     * {@code {header1=value1, header2=[value2, value3]}}.
     */
    private String headersToString(final MultivaluedMap<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return "";
        }

        final StringBuilder sb = new StringBuilder();
        for (final Map.Entry<String, List<String>> entry : headers.entrySet()) {
            sb.append(entry.getKey());
            sb.append("=");

            // Redact the PSK secret for the headers' value.
            if (entry.getKey().contains("psk")) {
                sb.append("REDACTED");
            } else {
                // When the headers contain a single value, spare the square
                // brackets.
                if (entry.getValue().size() == 1) {
                    sb.append(entry.getValue().getFirst());
                } else {
                    sb.append(entry.getValue());
                }

                sb.append(",");
            }
        }

        return sb.toString();
    }
}
