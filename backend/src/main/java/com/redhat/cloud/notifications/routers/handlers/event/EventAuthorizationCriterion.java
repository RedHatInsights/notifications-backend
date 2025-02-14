package com.redhat.cloud.notifications.routers.handlers.event;

import com.redhat.cloud.notifications.ingress.RecipientsAuthorizationCriterion;
import java.util.UUID;

public record EventAuthorizationCriterion(
    UUID id,
    RecipientsAuthorizationCriterion authorizationCriterion
) {
}
