package com.redhat.cloud.notifications.db.model;

import com.redhat.cloud.notifications.routers.handlers.endpoint.EndpointResourceTest;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A helper class for the {@link com.redhat.cloud.notifications.db.ResourceHelpers#createTestEndpoints(String, String, int)}
 * function. It then gets used in {@link EndpointResourceTest#testSortingOrder()} and {@link EndpointResourceTest#testActive()}.
 */
public final class Stats {
    private final int createdEndpointsCount;
    private final Set<UUID> createdEndpointIds;
    private int disabledCount;
    private int webhookCount;

    public Stats(final int createdEndpointsCount) {
        this.createdEndpointsCount = createdEndpointsCount;
        this.createdEndpointIds = new HashSet<>(createdEndpointsCount);
    }

    public int getCreatedEndpointsCount() {
        return this.createdEndpointsCount;
    }

    public void increaseDisabledCount() {
        this.disabledCount++;
    }

    public int getDisabledCount() {
        return this.disabledCount;
    }

    public int getWebhookCount() {
        return this.webhookCount;
    }

    public void increaseWebhookCount() {
        this.webhookCount++;
    }

    public void addEndpointId(final UUID endpointId) {
        this.createdEndpointIds.add(endpointId);
    }

    public Set<UUID> getEndpointIds() {
        return this.createdEndpointIds;
    }
}
