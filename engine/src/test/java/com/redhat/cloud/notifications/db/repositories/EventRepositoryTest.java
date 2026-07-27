package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.EngineConfig;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class EventRepositoryTest {

    private Bundle createdBundle;
    private Application createdApplication;
    private EventType createdEventType;
    private final List<Event> createdEvents = new ArrayList<>(5);

    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);

    @Inject
    EntityManager entityManager;

    @Inject
    EventRepository eventRepository;

    @Inject
    ResourceHelpers resourceHelpers;

    @InjectMock
    EngineConfig engineConfig;

    /**
     * Inserts five event fixtures in the database. The fixtures then get
     * their "created at" timestamp modified by removing days from their dates.
     * The first one will have "today - 1 days" as the creation date, the
     * second one will be "today - 2 days" etc.
     */
    @BeforeEach
    @Transactional
    void insertEventFixtures() {
        // Use a page size comfortably larger than the number of fixtures, so
        // that the existing tests keep exercising a single page. The
        // pagination-specific test below overrides this with a smaller size.
        when(engineConfig.getEventsExportPageSize()).thenReturn(100);

        this.createdBundle = this.resourceHelpers.createBundle("test-engine-event-repository-bundle");
        this.createdApplication = this.resourceHelpers.createApp(this.createdBundle.getId(), "test-engine-event-repository-application");
        this.createdEventType = this.resourceHelpers.createEventType(this.createdApplication.getId(), "test-engine-event-repository-event-type");

        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        // Create five events which will be used in the tests.
        for (int i = 0; i < 5; i++) {
            final Event event = new Event();

            event.setAccountId("account-id");
            event.setOrgId(DEFAULT_ORG_ID);
            event.setEventType(this.createdEventType);
            event.setEventTypeDisplayName(this.createdEventType.getDisplayName());
            event.setApplicationId(this.createdApplication.getId());
            event.setApplicationDisplayName(this.createdApplication.getDisplayName());
            event.setBundleId(this.createdBundle.getId());
            event.setBundleDisplayName(this.createdBundle.getDisplayName());
            event.setCreated(now.minusDays(i + 1));

            this.entityManager.persist(event);

            this.createdEvents.add(event);
        }
    }

    /**
     * Removes the created fixtures in the database.
     */
    @AfterEach
    @Transactional
    void removeFixtures() {
        this.entityManager.createQuery("DELETE FROM Event WHERE id IN :uuids").setParameter("uuids", this.createdEvents.stream().map(Event::getId).collect(Collectors.toList()));
        this.entityManager.createQuery("DELETE FROM EventType WHERE id = :uuid").setParameter("uuid", this.createdEventType.getId()).executeUpdate();
        this.entityManager.createQuery("DELETE FROM Application WHERE id = :uuid").setParameter("uuid", this.createdApplication.getId()).executeUpdate();
        this.entityManager.createQuery("DELETE FROM Bundle WHERE id = :uuid").setParameter("uuid", this.createdBundle.getId()).executeUpdate();
    }

    /**
     * Drains the given pages iterator into a single flat, ordered list of
     * events.
     */
    private static List<Event> drain(final Iterator<List<Event>> pages) {
        final List<Event> result = new ArrayList<>();

        while (pages.hasNext()) {
            result.addAll(pages.next());
        }

        return result;
    }

    /**
     * Tests that when no date ranges are provided all the events related to
     * the org id are fetched.
     * Tests both denormalized (false) and normalized (true) query modes.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testGetAll(boolean useNormalizedQueries) {
        when(engineConfig.isNormalizedQueriesEnabled(anyString())).thenReturn(useNormalizedQueries);

        final List<Event> result = drain(this.eventRepository.findEventsToExport(DEFAULT_ORG_ID, null, null));

        Assertions.assertEquals(this.createdEvents.size(), result.size(), "unexpected number of fetched events");
        Assertions.assertIterableEquals(
            this.createdEvents.stream().sorted(Comparator.comparing(evt -> evt.getCreated())).toList(),
            result.stream().sorted(Comparator.comparing(evt -> evt.getCreated())).toList(),
            "the fetched events are not the same as the created ones");
    }

    /**
     * Tests that when just the "from" date is provided, the events are
     * filtered as expected.
     * Tests both denormalized (false) and normalized (true) query modes.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testGetJustFrom(boolean useNormalizedQueries) {
        when(engineConfig.isNormalizedQueriesEnabled(anyString())).thenReturn(useNormalizedQueries);

        final LocalDate fourDaysAgo = TODAY.minusDays(4);

        final List<Event> result = drain(this.eventRepository.findEventsToExport(DEFAULT_ORG_ID, fourDaysAgo, null));

        Assertions.assertEquals(4, result.size(), "unexpected number of events received when applying the 'from' filter to four days ago");

        for (final Event event : result) {
            final LocalDate eventDate = event.getCreated().toLocalDate();

            Assertions.assertTrue(
                eventDate.compareTo(fourDaysAgo) >= 0,
                String.format(
                    "the event doesn't have a date greater or equal than the specified \"from\" filter. \"from\" filter date: %s. Event date: %s",
                    fourDaysAgo,
                    eventDate
                )
            );
        }
    }


    /**
     * Tests that when just the "to" date is provided, the events are filtered
     * as expected.
     * Tests both denormalized (false) and normalized (true) query modes.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testGetJustTo(boolean useNormalizedQueries) {
        when(engineConfig.isNormalizedQueriesEnabled(anyString())).thenReturn(useNormalizedQueries);

        final LocalDate threeDaysAgo = TODAY.minusDays(3);

        final List<Event> result = drain(this.eventRepository.findEventsToExport(DEFAULT_ORG_ID, null, threeDaysAgo));

        Assertions.assertEquals(3, result.size(), "unexpected number of events received when applying the 'to' filter to three days ago");

        for (final Event event : result) {
            final LocalDate eventDate = event.getCreated().toLocalDate();

            Assertions.assertTrue(
                eventDate.compareTo(threeDaysAgo) <= 0,
                String.format(
                    "the event doesn't have a date less or equal than the specified \"to\" filter. \"to\" filter date: %s. Event date: %s",
                    threeDaysAgo,
                    eventDate
                )
            );
        }
    }

    /**
     * Tests that when a date range is provided, only the events that comply
     * with that range are fetched.
     * Tests both denormalized (false) and normalized (true) query modes.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testGetDateRange(boolean useNormalizedQueries) {
        when(engineConfig.isNormalizedQueriesEnabled(anyString())).thenReturn(useNormalizedQueries);

        final LocalDate fourDaysAgo = TODAY.minusDays(4);
        final LocalDate threeDaysAgo = TODAY.minusDays(3);

        final List<Event> result = drain(this.eventRepository.findEventsToExport(DEFAULT_ORG_ID, fourDaysAgo, threeDaysAgo));

        Assertions.assertEquals(2, result.size(), "unexpected number of events received when applying the 'from' filter to four days ago, and the 'to' filter to three days ago");

        for (final Event event : result) {
            final LocalDate eventDate = event.getCreated().toLocalDate();

            Assertions.assertTrue(
                eventDate.compareTo(fourDaysAgo) >= 0,
                String.format(
                    "the event doesn't have a date greater or equal than the specified \"from\" filter. \"from\" filter date: %s. Event date: %s",
                    threeDaysAgo,
                    eventDate
                )
            );

            Assertions.assertTrue(
                eventDate.compareTo(threeDaysAgo) <= 0,
                String.format(
                    "the event doesn't have a date less or equal than the specified \"to\" filter. \"to\" filter date: %s. Event date: %s",
                    threeDaysAgo,
                    eventDate
                )
            );
        }
    }

    /**
     * Tests that when the configured export page size is not a positive
     * integer, {@code findEventsToExport} fails fast with an
     * {@link IllegalStateException} instead of silently returning no events.
     */
    @ParameterizedTest
    @ValueSource(ints = {0, -1, -100})
    void testGetAllInvalidPageSize(int invalidPageSize) {
        when(engineConfig.getEventsExportPageSize()).thenReturn(invalidPageSize);

        Assertions.assertThrows(
            IllegalStateException.class,
            () -> this.eventRepository.findEventsToExport(DEFAULT_ORG_ID, null, null),
            "expected an IllegalStateException to be thrown when the configured page size is not a positive integer");
    }

    /**
     * Tests that when the configured export page size is smaller than the
     * total number of matching events, {@code findEventsToExport} still
     * returns every event, in order, across multiple pages.
     * Tests both denormalized (false) and normalized (true) query modes.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testGetAllPaginated(boolean useNormalizedQueries) {
        when(engineConfig.isNormalizedQueriesEnabled(anyString())).thenReturn(useNormalizedQueries);
        // Force a page size smaller than the number of fixture events, so
        // that the pagination loop has to run for more than one page.
        when(engineConfig.getEventsExportPageSize()).thenReturn(2);

        final List<List<Event>> pages = new ArrayList<>();
        final Iterator<List<Event>> iterator = this.eventRepository.findEventsToExport(DEFAULT_ORG_ID, null, null);

        while (iterator.hasNext()) {
            pages.add(iterator.next());
        }

        // Five fixtures with a page size of two: three pages, the last one
        // partially filled.
        Assertions.assertEquals(3, pages.size(), "unexpected number of pages fetched");
        Assertions.assertEquals(2, pages.get(0).size(), "unexpected size for the first page");
        Assertions.assertEquals(2, pages.get(1).size(), "unexpected size for the second page");
        Assertions.assertEquals(1, pages.get(2).size(), "unexpected size for the third and last page");

        final List<Event> result = pages.stream().flatMap(List::stream).toList();

        Assertions.assertEquals(this.createdEvents.size(), result.size(), "unexpected total number of fetched events");
        Assertions.assertIterableEquals(
            this.createdEvents.stream().sorted(Comparator.comparing(Event::getCreated)).toList(),
            result,
            "the fetched events are not the same as the created ones, or were not returned in ascending 'created' order across pages");
    }

    /**
     * Tests that when two events share the exact same "created" timestamp
     * and a page boundary falls in the middle of that tied group, the
     * keyset cursor's tie-break clause ({@code e.created = :cursorCreated
     * AND e.id > :cursorId}) is exercised, and every event is still
     * returned exactly once, in the correct order, without duplicates or
     * omissions.
     * Tests both denormalized (false) and normalized (true) query modes.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @Transactional
    void testGetAllPaginatedTiedCreatedTimestamps(boolean useNormalizedQueries) {
        when(engineConfig.isNormalizedQueriesEnabled(anyString())).thenReturn(useNormalizedQueries);

        // Insert four extra fixtures which all share the exact same
        // "created" timestamp, older than every other fixture, so that a
        // page boundary can be forced to fall right in the middle of the
        // tied group.
        final LocalDateTime tiedCreated = LocalDateTime.now(ZoneOffset.UTC).minusDays(10);
        final List<Event> tiedEvents = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            final Event event = new Event();

            event.setAccountId("account-id");
            event.setOrgId(DEFAULT_ORG_ID);
            event.setEventType(this.createdEventType);
            event.setEventTypeDisplayName(this.createdEventType.getDisplayName());
            event.setApplicationId(this.createdApplication.getId());
            event.setApplicationDisplayName(this.createdApplication.getDisplayName());
            event.setBundleId(this.createdBundle.getId());
            event.setBundleDisplayName(this.createdBundle.getDisplayName());
            event.setCreated(tiedCreated);

            this.entityManager.persist(event);

            tiedEvents.add(event);
        }

        this.createdEvents.addAll(tiedEvents);

        // Force a page size of two so that, ordered by (created, id), the
        // boundary between the first and second page falls right in the
        // middle of the tied group.
        when(engineConfig.getEventsExportPageSize()).thenReturn(2);

        final List<Event> result = drain(this.eventRepository.findEventsToExport(DEFAULT_ORG_ID, null, null));

        Assertions.assertEquals(this.createdEvents.size(), result.size(), "unexpected number of fetched events; some tied events were likely duplicated or dropped at a page boundary");
        Assertions.assertEquals(
            this.createdEvents.stream().map(Event::getId).collect(Collectors.toSet()),
            result.stream().map(Event::getId).collect(Collectors.toSet()),
            "the fetched event ids do not match the created ones; some tied events were likely duplicated or dropped at a page boundary");

        // "created" cannot discriminate the order among the tied events, so
        // the tie-break clause on "id" must have kicked in: they should
        // come back sorted by ascending id, as Postgres compares its "uuid"
        // column type: byte-wise (unsigned), unlike UUID#compareTo, which
        // compares the most/least significant bits as signed longs.
        final Comparator<UUID> unsignedPostgresUuidOrder = (a, b) -> {
            final int msbCompare = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());

            return msbCompare != 0 ? msbCompare : Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits());
        };

        final List<UUID> expectedTiedIds = tiedEvents.stream().map(Event::getId).sorted(unsignedPostgresUuidOrder).toList();
        final List<UUID> actualTiedIds = result.stream()
            .filter(evt -> expectedTiedIds.contains(evt.getId()))
            .map(Event::getId)
            .toList();

        Assertions.assertEquals(expectedTiedIds, actualTiedIds, "the tied events were not returned in ascending 'id' order, as expected from the cursor's tie-break clause");
    }
}
