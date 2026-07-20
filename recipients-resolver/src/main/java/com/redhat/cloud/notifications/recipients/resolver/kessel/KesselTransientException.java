package com.redhat.cloud.notifications.recipients.resolver.kessel;

import io.grpc.StatusRuntimeException;
import org.project_kessel.api.auth.OAuth2Exception;

/**
 * Marker exception for transient Kessel client failures. Only this exception type triggers retry.
 */
public class KesselTransientException extends RuntimeException {
    public KesselTransientException(StatusRuntimeException cause) {
        super("Transient Kessel failure: " + cause.getStatus().getCode(), cause);
    }

    public KesselTransientException(OAuth2Exception cause) {
        super("Transient Kessel failure: OAuth2 credentials fetch failed", cause);
    }
}
