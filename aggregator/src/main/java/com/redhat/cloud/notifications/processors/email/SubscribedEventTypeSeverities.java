package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.Severity;
import java.util.Map;
import java.util.UUID;

public record SubscribedEventTypeSeverities(
    UUID eventTypeId,
    Map<Severity, Boolean> severities
) { }

