package com.redhat.cloud.notifications.exports.transformers;

import java.util.List;

/**
 * Receives one page of results at a time, so that a caller can stream a
 * large result set through a {@link ResultsTransformer} without ever
 * materializing the full result set in memory.
 * @param <T> the type of the entity contained in each page.
 */
@FunctionalInterface
public interface PageConsumer<T> {
    /**
     * Handles a single page of results.
     * @param page the page of results to handle.
     * @throws TransformationException if the page could not be transformed.
     */
    void accept(List<T> page) throws TransformationException;
}
