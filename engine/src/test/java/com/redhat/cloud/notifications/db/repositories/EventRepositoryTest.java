package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.StatelessSessionFactory;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class EventRepositoryTest {

    private Bundle createdBundle;
    private Application createdApplication;
    private EventType createdEventType;
    private final List<Event> createdEvents = new ArrayList<>(5);

    @Inject
    EntityManager entityManager;

    @Inject
    EventRepository eventRepository;

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    StatelessSessionFactory statelessSessionFactory;

    /**
     * Inserts five event fixtures in the database. The fixtures then get
     * their "created at" timestamp modified by removing days from their dates.
     * The first one will have "today - 1 days" as the creation date, the
     * second one will be "today - 2 days" etc.
     */
    @BeforeEach
    @Transactional
    void insertEventFixtures() {
        this.createdBundle = this.resourceHelpers.createBundle("test-engine-event-repository-bundle");
        this.createdApplication = this.resourceHelpers.createApp(this.createdBundle.getId(), "test-engine-event-repository-application");
        this.createdEventType = this.resourceHelpers.createEventType(this.createdApplication.getId(), "test-engine-event-repository-event-type");

        // Create five events which will be used in the tests.
        for (int i = 0; i < 5; i++) {
            final Event event = new Event();

            event.setId(UUID.randomUUID());
            event.setAccountId("account-id");
            event.setOrgId(DEFAULT_ORG_ID);
            event.setEventType(this.createdEventType);
            event.setEventTypeDisplayName(this.createdEventType.getDisplayName());
            event.setApplicationId(this.createdApplication.getId());
            event.setApplicationDisplayName(this.createdApplication.getDisplayName());
            event.setBundleId(this.createdBundle.getId());
            event.setBundleDisplayName(this.createdBundle.getDisplayName());

            this.entityManager.persist(event);

            this.createdEvents.add(event);
        }

        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        // Modify the events' creation date.
        for (int i = 0; i < 5; i++) {
            final Event event = this.createdEvents.get(i);
            event.setCreated(now.minusDays(i + 1));

            this.entityManager.persist(event);
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
     * Tests that when no date ranges are provided all the events related to
     * the org id are fetched.
     */
    @Test
    void testGetAll() {
        this.statelessSessionFactory.withSession(session -> {
            final List<Event> result = this.eventRepository.findEventsToExport(DEFAULT_ORG_ID, null, null);

            Assertions.assertEquals(this.createdEvents.size(), result.size(), "unexpected number of fetched events");
            Assertions.assertIterableEquals(this.createdEvents, result, "the fetched events are not the same as the created ones");
        });
    }

    /**
     * Tests that when just the "from" date is provided, the events are
     * filtered as expected.
     */
    @Test
    void testGetJustFrom() {
        final LocalDate today = LocalDate.now();
        final LocalDate fourDaysAgo = today.minusDays(4);

        this.statelessSessionFactory.withSession(session -> {
            final List<Event> result = this.eventRepository.findEventsToExport(DEFAULT_ORG_ID, fourDaysAgo, null);

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
        });
    }


    /**
     * Tests that when just the "to" date is provided, the events are filtered
     * as expected.
     */
    @Test
    void testGetJustTo() {
        final LocalDate today = LocalDate.now();
        final LocalDate threeDaysAgo = today.minusDays(3);

        this.statelessSessionFactory.withSession(session -> {
            final List<Event> result = this.eventRepository.findEventsToExport(DEFAULT_ORG_ID, null, threeDaysAgo);

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
        });
    }

    /**
     * Tests that when a date range is provided, only the events that comply
     * with that range are fetched.
     */
    @Test
    void testGetDateRange() {
        final LocalDate today = LocalDate.now();
        final LocalDate fourDaysAgo = today.minusDays(4);
        final LocalDate threeDaysAgo = today.minusDays(3);

        this.statelessSessionFactory.withSession(session -> {
            final List<Event> result = this.eventRepository.findEventsToExport(DEFAULT_ORG_ID, fourDaysAgo, threeDaysAgo);

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
        });
    }

    /**
     * Tests that when no dates are provided the function under test does not
     * raise any exceptions.
     */
    @Test
    void testNoDates() {
        this.eventRepository.validateFromTo(null, null);
    }

    /**
     * Tests that when proper "from" and "to" dates are provided to the
     * function under test, no exceptions are raised.
     */
    @Test
    void testValidDates() {
        final LocalDate today = LocalDate.now();

        this.eventRepository.validateFromTo(today.minusDays(10), today.minusDays(5));
    }

    /**
     * Tests that "from" dates that are in the future cause an exception to
     * raise.
     */
    @Test
    void testInvalidFromDateFuture() {
        final LocalDate today = LocalDate.now();

        final IllegalStateException exception = Assertions.assertThrows(
            IllegalStateException.class,
            () -> this.eventRepository.validateFromTo(today.plusDays(1), null)
        );

        Assertions.assertEquals("can't fetch events from the future!", exception.getMessage(), "unexpected error message when validating a 'from' date from the future");
    }

    /**
     * Tests that "from" dates that are older than a month cause an exception
     * to raise.
     */
    @Test
    void testInvalidFromDateOlderMonth() {
        final LocalDate today = LocalDate.now();

        final IllegalStateException exception = Assertions.assertThrows(
            IllegalStateException.class,
            () -> this.eventRepository.validateFromTo(today.minusMonths(1).minusDays(1), null)
        );

        Assertions.assertEquals("events that are older than a month cannot be fetched", exception.getMessage(), "unexpected error message when validating a 'from' date older than a month");
    }

    /**
     * Tests that "from" dates that are after "to" dates cause an exception to
     * raise.
     */
    @Test
    void testInvalidFromDateAfterToDate() {
        final LocalDate today = LocalDate.now();

        final IllegalStateException exception = Assertions.assertThrows(
            IllegalStateException.class,
            () -> this.eventRepository.validateFromTo(today.minusDays(5), today.minusDays(10))
        );

        Assertions.assertEquals("the 'to' date cannot be lower than the 'from' date", exception.getMessage(), "unexpected error message when validating a 'from' date which is after a 'to' date");
    }

    /**
     * Tests that "to" dates that are in the future cause an exception to
     * raise.
     */
    @Test
    void testInvalidToDateFuture() {
        final LocalDate today = LocalDate.now();

        final IllegalStateException exception = Assertions.assertThrows(
            IllegalStateException.class,
            () -> this.eventRepository.validateFromTo(null, today.plusDays(1))
        );

        Assertions.assertEquals("can't fetch events from the future!", exception.getMessage(), "unexpected error message when validating a 'to' date from the future");
    }

    /**
     * Tests that "to" dates that are older than a month cause an exception to
     * raise.
     */
    @Test
    void testInvalidToDateOlderMonth() {
        final LocalDate today = LocalDate.now();

        final IllegalStateException exception = Assertions.assertThrows(
            IllegalStateException.class,
            () -> this.eventRepository.validateFromTo(null, today.minusMonths(1).minusDays(1))
        );

        Assertions.assertEquals("events that are older than a month cannot be fetched", exception.getMessage(), "unexpected error message when validating a 'to' date older than a month");
    }

    String generateRandomString() {
        final byte[] array = new byte[50];
        final Random random = new Random();
        random.nextBytes(array);

        return new String(array, StandardCharsets.UTF_8);
    }
}