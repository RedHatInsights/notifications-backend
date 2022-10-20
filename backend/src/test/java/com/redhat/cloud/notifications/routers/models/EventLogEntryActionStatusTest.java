package com.redhat.cloud.notifications.routers.models;

import com.redhat.cloud.notifications.models.NotificationStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EventLogEntryActionStatusTest {

    @Test
    public void fromNotificationStatusTest() {
        assertEquals(
                EventLogEntryActionStatus.SENT,
                EventLogEntryActionStatus.fromNotificationStatus(NotificationStatus.SENT)
        );

        assertEquals(
                EventLogEntryActionStatus.SUCCESS,
                EventLogEntryActionStatus.fromNotificationStatus(NotificationStatus.SUCCESS)
        );

        assertEquals(
                EventLogEntryActionStatus.PROCESSING,
                EventLogEntryActionStatus.fromNotificationStatus(NotificationStatus.PROCESSING)
        );

        assertEquals(
                EventLogEntryActionStatus.FAILED,
                EventLogEntryActionStatus.fromNotificationStatus(NotificationStatus.FAILED_INTERNAL)
        );

        assertEquals(
                EventLogEntryActionStatus.FAILED,
                EventLogEntryActionStatus.fromNotificationStatus(NotificationStatus.FAILED_EXTERNAL)
        );
    }

}
