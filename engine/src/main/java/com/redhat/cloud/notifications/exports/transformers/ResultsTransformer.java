package com.redhat.cloud.notifications.exports.transformers;

import java.util.List;

/**
 * Defines the operations to be performed by a result transformer. The goal of
 * these transformers is to transform the given entities to the format the
 * export service takes.
 * <p>
 * Unlike a one-shot {@code transform(List<T>)}, this interface is meant to be
 * fed incrementally, one page of results at a time via {@link #addRecords},
 * so that a large result set never has to be fully materialized alongside
 * its rendered output at the same time. Call {@link #finish()} once every
 * page has been added to obtain the final serialized contents.
 * @param <T> the type of the entity to be transformed.
 */
public interface ResultsTransformer<T> {
    /**
     * Adds a page of results to the transformer's output.
     * @param records the page of results to add.
     * @throws TransformationException if any error occurs while adding the
     * records to the transformer's output.
     */
    void addRecords(List<T> records) throws TransformationException;

    /**
     * Finalizes the transformation and returns the serialized contents built
     * from every page previously added via {@link #addRecords}.
     * @return a {@link String} with the transformed contents.
     * @throws TransformationException if any error occurs while finalizing
     * the transformation.
     */
    String finish() throws TransformationException;
}
