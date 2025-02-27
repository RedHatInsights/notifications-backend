package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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

    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);

    @Inject
    EntityManager entityManager;

    @Inject
    EventRepository eventRepository;

    @Inject
    ResourceHelpers resourceHelpers;

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

        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

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
     * Tests that when no date ranges are provided all the events related to
     * the org id are fetched.
     */
    @Test
    void testGetAll() {
        final List<Event> result = this.eventRepository.findEventsToExport(DEFAULT_ORG_ID, null, null);

        Assertions.assertEquals(this.createdEvents.size(), result.size(), "unexpected number of fetched events");
        Assertions.assertIterableEquals(
            this.createdEvents.stream().sorted(Comparator.comparing(evt -> evt.getCreated())).toList(),
            result.stream().sorted(Comparator.comparing(evt -> evt.getCreated())).toList(),
            "the fetched events are not the same as the created ones");
    }

    /**
     * Tests that when just the "from" date is provided, the events are
     * filtered as expected.
     */
    @Test
    void testGetJustFrom() {
        final LocalDate fourDaysAgo = TODAY.minusDays(4);

        final List<Event> result = this.eventRepository.findEventsToExport(DEFAULT_ORG_ID, fourDaysAgo, null);

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
     */
    @Test
    void testGetJustTo() {
        final LocalDate threeDaysAgo = TODAY.minusDays(3);

        final List<Event> result = this.eventRepository.findEventsToExport(DEFAULT_ORG_ID, null, threeDaysAgo);

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
     */
    @Test
    void testGetDateRange() {
        final LocalDate fourDaysAgo = TODAY.minusDays(4);
        final LocalDate threeDaysAgo = TODAY.minusDays(3);

        final List<Event> result = this.eventRepository.findEventsToExport(DEFAULT_ORG_ID, fourDaysAgo, threeDaysAgo);

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
}
