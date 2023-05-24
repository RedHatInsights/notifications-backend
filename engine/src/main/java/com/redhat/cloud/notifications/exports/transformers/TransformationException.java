package com.redhat.cloud.notifications.exports.transformers;

/**
 * A wrapper exception class which wraps any transformation errors.
 */
public class TransformationException extends Exception {
    public TransformationException(final Throwable e) {
        super(e);
    }
}
