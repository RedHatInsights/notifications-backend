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

    private final StringWriter stringWriter = new StringWriter();
    private final CSVPrinter csvPrinter;

    public CSVEventTransformer() throws TransformationException {
        final CSVFormat csvFormat = CSVFormat.DEFAULT
            .builder()
            .setHeader(CSV_HEADERS)
            .setRecordSeparator(System.lineSeparator())
            .build();

        try {
            csvPrinter = new CSVPrinter(stringWriter, csvFormat);
        } catch (final IOException e) {
            throw new TransformationException(e);
        }
    }

    /**
     * Appends the given page of events as CSV records.
     * @param events the page of events to append.
     */
    @Override
    public void addRecords(final List<Event> events) throws TransformationException {
        try {
            for (final Event event : events) {
                csvPrinter.printRecord(
                    event.getId(),
                    event.getBundleDisplayName(),
                    event.getApplicationDisplayName(),
                    event.getEventTypeDisplayName(),
                    event.getCreated().toInstant(ZoneOffset.UTC)
                );
            }
        } catch (final IOException e) {
            throw new TransformationException(e);
        }
    }

    /**
     * Closes the underlying CSV printer and returns the accumulated CSV
     * contents.
     * @return a {@link String} with the transformed contents.
     */
    @Override
    public String finish() throws TransformationException {
        try {
            csvPrinter.close();

            return stringWriter.toString();
        } catch (final IOException e) {
            throw new TransformationException(e);
        }
    }
}
