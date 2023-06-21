package com.redhat.cloud.notifications.exports;

/**
 * Represents the expected payload for an error request in the "export service".
 * @param error   the HTTP code of the error.
 * @param message the error message describing what went wrong.
 */
public record ExportError(int error, String message) { }
