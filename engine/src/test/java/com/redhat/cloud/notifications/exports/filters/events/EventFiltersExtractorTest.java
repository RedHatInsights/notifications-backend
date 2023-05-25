package com.redhat.cloud.notifications.exports.filters.events;

import com.redhat.cloud.event.apps.exportservice.v1.ExportRequestClass;
import com.redhat.cloud.notifications.exports.filters.FilterExtractionException;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@QuarkusTest
public class EventFiltersExtractorTest {
    private final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);

    @Inject
    EventFiltersExtractor eventFiltersExtractor;

    /**
     * Tests that when no dates are provided the function under test does not
     * raise any exceptions.
     * @throws FilterExtractionException if any unexpected error occurs during
     *                                   the extraction of the filters.
     */
    @Test
    void testNoDate() throws FilterExtractionException {
        final EventFilters eventFilters = this.eventFiltersExtractor.extract(new ExportRequestClass());

        Assertions.assertNull(eventFilters.from(), "on empty filters the 'from' date should be null, since that means that no initial date filter has been specified");
        Assertions.assertNull(eventFilters.to(), "on empty filters the 'to' date should be null, since that means that no final date filter has been specified");
    }

    /**
     * Tests that when proper dates are provided in the filters, they are
     * correctly extracted from the map.
     * @throws FilterExtractionException if any unexpected error occurs during
     *                                   the extraction of the filters.
     */
    @Test
    void testValidDates() throws FilterExtractionException {
        final ExportRequestClass exportRequestClass = new ExportRequestClass();
        exportRequestClass.setFilters(
            Map.of(
                EventFiltersExtractor.FILTER_DATE_FROM,
                this.TODAY.minusDays(2).toString(),
                EventFiltersExtractor.FILTER_DATE_TO,
                this.TODAY.minusDays(1).toString()
            )
        );

        final EventFilters result = this.eventFiltersExtractor.extract(exportRequestClass);

        Assertions.assertEquals(this.TODAY.minusDays(2), result.from(), "the 'from' date was not correctly extracted from the Cloud Event's payload");
        Assertions.assertEquals(this.TODAY.minusDays(1), result.to(), "the 'to' date was not correctly extracted from the Cloud Event's payload");
    }

    /**
     * Tests that when an unparseable "from" filter is received in the Cloud
     * Event's filters, an exception with the proper message is raised.
     */
    @Test
    void testUnparseableFromDateRaisesException() {
        final ExportRequestClass exportRequest = new ExportRequestClass();
        exportRequest.setFilters(
            Map.of(
                EventFiltersExtractor.FILTER_DATE_FROM,
                "Hello, World!"
            )
        );

        final FilterExtractionException exception = Assertions.assertThrows(
            FilterExtractionException.class,
            () -> this.eventFiltersExtractor.extract(exportRequest)
        );

        Assertions.assertEquals("unable to parse the 'from' date filter with the 'yyyy-mm-dd' format", exception.getMessage(), "unexpected error message in the FilterExtractionException when an unparseable 'from' date is present in the filters");
    }

    /**
     * Tests that when an unparseable "to" filter is received in the Cloud
     * Event's filters, an exception with the proper message is raised.
     */
    @Test
    void testUnparseableToDateRaisesException() {
        final ExportRequestClass exportRequest = new ExportRequestClass();
        exportRequest.setFilters(
            Map.of(
                EventFiltersExtractor.FILTER_DATE_TO,
                "Hello, World!"
            )
        );

        final FilterExtractionException exception = Assertions.assertThrows(
            FilterExtractionException.class,
            () -> this.eventFiltersExtractor.extract(exportRequest)
        );

        Assertions.assertEquals("unable to parse the 'to' date filter with the 'yyyy-mm-dd' format", exception.getMessage(), "unexpected error message in the FilterExtractionException when an unparseable 'from' date is present in the filters");
    }

    /**
     * Tests that when invalid "from" filters are received in the Cloud Event's
     * filters, exceptions with the proper message are raised.
     */
    @Test
    void testInvalidFromDatesRaiseExceptions() {
        record TestCase(String dateUnderTest, String expectedExceptionMessage) { }

        final List<TestCase> testCases = new ArrayList<>(2);

        // Invalid future dates.
        testCases.add(
            new TestCase(
                this.TODAY.plusDays(1).toString(),
                "invalid 'from' filter date specified: the specified date is in the future"
            )
        );

        // Invalid "older than a month" dates.
        testCases.add(
            new TestCase(
                this.TODAY.minusMonths(1).minusDays(1).toString(),
                "invalid 'from' filter date specified: the specified date is older than a month"
            )
        );

        for (final TestCase testCase : testCases) {
            final ExportRequestClass exportRequest = new ExportRequestClass();
            exportRequest.setFilters(
                Map.of(
                    EventFiltersExtractor.FILTER_DATE_FROM,
                    testCase.dateUnderTest()
                )
            );

            final FilterExtractionException exception = Assertions.assertThrows(
                FilterExtractionException.class,
                () -> this.eventFiltersExtractor.extract(exportRequest)
            );

            Assertions.assertEquals(testCase.expectedExceptionMessage(), exception.getMessage(), String.format("unexpected error message for test case %s", testCase));
        }
    }

    /**
     * Tests that when invalid "to" filters are received in the Cloud Event's
     * filters, exceptions with the proper message are raised.
     */
    @Test
    void testInvalidToDatesRaiseExceptions() {
        record TestCase(String dateUnderTest, String expectedExceptionMessage) { }

        final List<TestCase> testCases = new ArrayList<>(2);

        // Invalid future dates.
        testCases.add(
            new TestCase(
                this.TODAY.plusDays(1).toString(),
                "invalid 'to' filter date specified: the specified date is in the future"
            )
        );

        // Invalid "older than a month" dates.
        testCases.add(
            new TestCase(
                this.TODAY.minusMonths(1).minusDays(1).toString(),
                "invalid 'to' filter date specified: the specified date is older than a month"
            )
        );

        for (final TestCase testCase : testCases) {
            final ExportRequestClass exportRequest = new ExportRequestClass();
            exportRequest.setFilters(
                Map.of(
                    EventFiltersExtractor.FILTER_DATE_TO,
                    testCase.dateUnderTest()
                )
            );

            final FilterExtractionException exception = Assertions.assertThrows(
                FilterExtractionException.class,
                () -> this.eventFiltersExtractor.extract(exportRequest)
            );

            Assertions.assertEquals(testCase.expectedExceptionMessage(), exception.getMessage(), String.format("unexpected error message for test case %s", testCase));
        }
    }

    /**
     * Tests that when the Cloud Event contains a final date filter which goes
     * before the initial date filter, an exception with the proper error
     * message is raised.
     */
    @Test
    void testToDatesBeforeFromDatesRaiseExceptions() {
        final ExportRequestClass exportRequest = new ExportRequestClass();
        exportRequest.setFilters(
            Map.of(
                EventFiltersExtractor.FILTER_DATE_FROM,
                this.TODAY.minusDays(5).toString(),
                EventFiltersExtractor.FILTER_DATE_TO,
                this.TODAY.minusDays(6).toString()
            )
        );

        final FilterExtractionException exception = Assertions.assertThrows(
            FilterExtractionException.class,
            () -> this.eventFiltersExtractor.extract(exportRequest)
        );

        Assertions.assertEquals("'from' date must be earlier than the 'to' date", exception.getMessage(), "unexpected error message when providing a final date filter which is before the initial date filter");
    }

    /**
     * Tests that {@code null} dates are considered valid.
     */
    @Test
    void testNullDates() {
        Assertions.assertNull(
            this.eventFiltersExtractor.extractDateFromObject(null),
            "null dates are considered valid since they mean that there is no filter to apply"
        );
    }

    /**
     * Tests that dates which are unparseable cause an exception to raise.
     */
    @Test
    void testUnparseableDate() {
        Assertions.assertThrows(
            DateTimeParseException.class,
            () -> this.eventFiltersExtractor.extractDateFromObject("Hello, World!")
        );
    }

    /**
     * Tests that dates that are in the future cause an exception to raise.
     */
    @Test
    void testInvalidDateFuture() {
        final Object inTheFuture = this.TODAY
            .plusDays(1)
            .toString();

        final IllegalStateException exception = Assertions.assertThrows(
            IllegalStateException.class,
            () -> this.eventFiltersExtractor.extractDateFromObject(inTheFuture)
        );

        Assertions.assertEquals("the specified date is in the future", exception.getMessage(), "unexpected error message when extracting and validating a date from the future");
    }

    /**
     * Tests that dates that are older than a month cause an exception to raise.
     */
    @Test
    void testInvalidDateOlderMonth() {
        final Object olderThanAMonthDate = this.TODAY
            .minusMonths(1)
            .minusDays(1)
            .toString();

        final IllegalStateException exception = Assertions.assertThrows(
            IllegalStateException.class,
            () -> this.eventFiltersExtractor.extractDateFromObject(olderThanAMonthDate)
        );

        Assertions.assertEquals("the specified date is older than a month", exception.getMessage(), "unexpected error message when extracting and validating a date older than a month");
    }
}
