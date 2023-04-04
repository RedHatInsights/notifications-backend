package com.redhat.cloud.notifications.routers.sources;

import javax.ws.rs.core.Response;
import java.lang.reflect.Method;

/**
 * A runtime exception that is thrown in {@link SourcesService} any time
 * Sources returns an unsuccessful response.
 */
public class SourcesRuntimeException extends RuntimeException {
    /**
     * The Java method that caused the exception.
     */
    private final Method method;

    /**
     * The received response from Sources.
     */
    private final Response response;

    public SourcesRuntimeException(final Method method, final Response response) {
        this.method = method;
        this.response = response;
    }

    public Method getMethod() {
        return this.method;
    }

    public Response getResponse() {
        return this.response;
    }
}
