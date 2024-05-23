package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonValue;

public enum EndpointType {
    WEBHOOK(false, false),
    EMAIL_SUBSCRIPTION(false, true),
    CAMEL(true, false),
    ANSIBLE(false, false),
    DRAWER(false, true);

    public final boolean requiresSubType;
    public final boolean isSystemEndpointType;

    EndpointType(boolean requiresSubType, boolean isSystemEndpointType) {
        this.requiresSubType = requiresSubType;
        this.isSystemEndpointType = isSystemEndpointType;
    }

    /**
     * Transforms the enum values to lowercase. It is required for the
     * following tests to work:
     * <ul>
     *     <li>
     *         {@link EndpointResourceTest#testAddEndpointDrawerSubscription}
     *         {@link EndpointResourceTest#testAddEndpointEmailSubscription}
     *         {@link EndpointResourceTest#testAnsibleEndpointCRUD}
     *     </li>
     * </ul>
     *
     * The reason for this is that we use the entities to build the payloads
     * that we send to the endpoints, and since the enum values get written in
     * uppercase letters by default, the target endpoints were not recognizing
     * the type of the endpoints being managed, since they expect the lowercase
     * values.
     *
     * @return the enum value in lowercase.
     */
    @JsonValue
    public String toLowerCase() {
        return this.toString().toLowerCase();
    }
}
