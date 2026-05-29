package com.redhat.cloud.notifications.mcp;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkus.logging.Log;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Shared utilities for MCP tools.
 */
public final class McpToolUtils {

    private McpToolUtils() {
    }

    public static UUID parseUuid(String paramName, String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new ToolCallException("Invalid UUID for " + paramName + ": " + value);
        }
    }

    public static LocalDate parseDate(String paramName, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new ToolCallException("Invalid date for " + paramName + ": " + value + ", expected yyyy-MM-dd");
        }
    }

    /**
     * Executes a REST client call, translating REST exceptions into MCP ToolCallExceptions.
     * Unexpected exceptions are left unhandled so the framework's default handler logs and
     * returns INTERNAL_ERROR automatically.
     */
    public static <T> T executeRestCall(String toolName, McpPrincipal principal, Supplier<T> restCall, MeterRegistry registry) {
        try {
            return restCall.get();
        } catch (WebApplicationException e) {
            int status = e.getResponse().getStatus();
            Log.warnf(e, "Tool '%s' failed (HTTP %d) for org %s, user %s",
                    toolName, status, principal.getOrgId(), principal.getUserId());
            registry.counter("notifications.mcp.tool.error", "tool", toolName, "type", "http_" + status).increment();
            throw new ToolCallException(httpErrorMessage(status));
        } catch (ProcessingException e) {
            Log.errorf(e, "Tool '%s' connection error for org %s, user %s",
                    toolName, principal.getOrgId(), principal.getUserId());
            registry.counter("notifications.mcp.tool.error", "tool", toolName, "type", "connection").increment();
            throw new ToolCallException("Backend service unavailable, try again later");
        }
    }

    public static String httpErrorMessage(int status) {
        return switch (status) {
            case 403 -> "Access denied";
            case 404 -> "Resource not found";
            default -> status >= 400 && status < 500
                    ? "Invalid request (HTTP " + status + ")"
                    : "Backend service error, try again later";
        };
    }
}
