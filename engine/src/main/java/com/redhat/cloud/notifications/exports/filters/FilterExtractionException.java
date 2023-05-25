package com.redhat.cloud.notifications.exports.filters;

/**
 * An exception that is thrown when the provided filters for the data to be
 * exported are wrong.
 */
public class FilterExtractionException extends Exception {
    public FilterExtractionException(final String errorMessage) {
        super(errorMessage);
    }
}
