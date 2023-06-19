package com.redhat.cloud.notifications.exports.transformers.event;

import com.redhat.cloud.notifications.exports.transformers.ResultsTransformer;
import com.redhat.cloud.notifications.exports.transformers.TransformationException;
import com.redhat.cloud.notifications.models.Event;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.io.StringWriter;
import java.time.ZoneOffset;
import java.util.List;

public final class CSVEventTransformer implements ResultsTransformer<Event> {

    private static final String[] CSV_HEADERS = {"uuid", "bundle", "application", "eventType", "created"};

    /**
     * Transforms the given list of events to CSV.
     * @param events the list of events to transform.
     * @return a {@link String} with the transformed contents.
     */
    @Override
    public String transform(final List<Event> events) throws TransformationException {
        // Set the format for the CSV file.
        final CSVFormat csvFormat = CSVFormat.DEFAULT
            .builder()
            .setHeader(CSV_HEADERS)
            .setRecordSeparator(System.lineSeparator())
            .build();

        final StringWriter stringWriter = new StringWriter();

        try (CSVPrinter csvPrinter = new CSVPrinter(stringWriter, csvFormat)) {
            for (final Event event : events) {
                csvPrinter.printRecord(
                    event.getId(),
                    event.getBundleDisplayName(),
                    event.getApplicationDisplayName(),
                    event.getEventTypeDisplayName(),
                    event.getCreated().toInstant(ZoneOffset.UTC)
                );
            }

            return stringWriter.toString();
        } catch (final IOException e) {
            throw new TransformationException(e);
        }
    }
}
