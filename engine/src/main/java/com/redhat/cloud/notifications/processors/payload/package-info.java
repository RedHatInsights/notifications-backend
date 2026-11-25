/**
 * Defines the mapper and DTOs which are used when the payloads are too big to
 * be sent to Kafka, and we want them to be stored in the database instead. The
 * {@code PayloadDetails} entity itself lives in the {@code common} module,
 * because the aggregator also writes to the same table.
 */
package com.redhat.cloud.notifications.processors.payload;
