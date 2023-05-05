package com.redhat.cloud.notifications.exports;

import java.util.List;

/**
 * Defines the operations to be performed by a result transformer. The goal of
 * these transformers is to transform the given entities to the format the
 * export service takes.
 * @param <T> the type of the entity to be transformed.
 */
public interface ResultsTransformer<T> {
    /**
     * Transforms the given list of resources to the end format to be sent to
     * the export service.
     * @param results the list of results to transform.
     * @return a {@link String} with the transformed contents.
     * @throws TransformationException if any error occurs during the
     * transformation of the results.
     */
    String transform(List<T> results) throws TransformationException;
}
