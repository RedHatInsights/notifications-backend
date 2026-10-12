package com.redhat.cloud.notifications.models.dto.v3.subscriptions;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.redhat.cloud.notifications.models.SubscriptionType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Map;

@Schema(enumeration = { "instant_email", "daily_email", "weekly_email", "drawer" })
public enum SubscriptionTypeDTO {
    @JsonProperty("instant_email")
    INSTANT("instant_email", SubscriptionType.INSTANT),
    @JsonProperty("daily_email")
    DAILY("daily_email", SubscriptionType.DAILY),
    @JsonProperty("weekly_email")
    WEEKLY("weekly_email", SubscriptionType.WEEKLY),
    @JsonProperty("drawer")
    DRAWER("drawer", SubscriptionType.DRAWER);

    private final String wireName;
    private final SubscriptionType entityType;

    SubscriptionTypeDTO(String wireName, SubscriptionType entityType) {
        this.wireName = wireName;
        this.entityType = entityType;
    }

    public String toWireName() {
        return wireName;
    }

    private static final Map<String, SubscriptionTypeDTO> WIRE_NAMES = Map.of(
        "instant_email", INSTANT,
        "daily_email", DAILY,
        "weekly_email", WEEKLY,
        "drawer", DRAWER
    );

    private static final Map<SubscriptionType, SubscriptionTypeDTO> BY_ENTITY = Map.of(
        SubscriptionType.INSTANT, INSTANT,
        SubscriptionType.DAILY, DAILY,
        SubscriptionType.WEEKLY, WEEKLY,
        SubscriptionType.DRAWER, DRAWER
    );

    // This may seem unused but it is actually required for RESTEasy @PathParam deserialization.
    public static SubscriptionTypeDTO fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Subscription type must not be null");
        }
        SubscriptionTypeDTO result = WIRE_NAMES.get(value);
        if (result != null) {
            return result;
        }
        throw new IllegalArgumentException("Invalid subscription type: '" + value + "'. Accepted values are: " + WIRE_NAMES.keySet());
    }

    public SubscriptionType toEntity() {
        return entityType;
    }

    public static SubscriptionTypeDTO fromEntity(SubscriptionType type) {
        SubscriptionTypeDTO result = BY_ENTITY.get(type);
        if (result == null) {
            throw new IllegalArgumentException("No SubscriptionTypeDTO mapping for: " + type);
        }
        return result;
    }
}
