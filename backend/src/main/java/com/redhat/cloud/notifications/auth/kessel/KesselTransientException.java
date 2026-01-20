package com.redhat.cloud.notifications.auth.kessel;

import io.grpc.StatusRuntimeException;

/**
 * Marker exception for transient Kessel client failures. Only this exception type triggers retry.
 */
public class KesselTransientException extends RuntimeException {
    public KesselTransientException(StatusRuntimeException cause) {
        super("Transient Kessel failure: " + cause.getStatus().getCode(), cause);
    }
}
