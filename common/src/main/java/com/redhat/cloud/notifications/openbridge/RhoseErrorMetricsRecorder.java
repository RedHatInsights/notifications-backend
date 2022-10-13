package com.redhat.cloud.notifications.openbridge;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.logging.Log;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static javax.ws.rs.core.Response.Status.Family;

@ApplicationScoped
public class RhoseErrorMetricsRecorder {

    public static String COUNTER_NAME = "notifications.rhose.errors";

    @Inject
    MeterRegistry meterRegistry;

    private final Map</* RHOSE REST API path */ String, Map</* HTTP status code */ Integer, Counter>> counters = new HashMap<>();

    public void record(String path, WebApplicationException e) {
        try {
            if (e.getResponse() != null) {
                int statusCode = e.getResponse().getStatus();
                if (Family.familyOf(statusCode) == Family.SERVER_ERROR) {
                    Map<Integer, Counter> pathCounters = counters.computeIfAbsent(path, k -> new HashMap<>());
                    pathCounters.computeIfAbsent(statusCode, buildCounter(path, statusCode)).increment();
                }
            }
        } catch (Exception ex) {
            Log.warn("RHOSE error metric recording failed", ex);
        }
    }

    private Function<Integer, Counter> buildCounter(String path, int statusCode) {
        return k -> meterRegistry.counter(COUNTER_NAME, "path", path, "statusCode", String.valueOf(statusCode));
    }
}
