package com.redhat.cloud.notifications.exports;

/**
 * Represents the expected payload for an error request in the "export service".
 */
public class ExportError {
    /**
     * The code is represented as a string since that is what the export
     * service expects.
     */
    private final String code;
    private final String message;

    /**
     * @param httpStatus the HTTP code of the error.
     * @param message the error message describing what went wrong.
     */
    public ExportError(final int httpStatus, final String message) {
        this.code = String.valueOf(httpStatus);
        this.message = message;
    }

    public String getCode() {
        return this.code;
    }

    public String getMessage() {
        return this.message;
    }
}
