package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.NotificationHistory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * This class is used to prevent reactive tests crazy nesting.
 */
public class ModelInstancesHolder {
    public List<Bundle> bundles = new ArrayList<>();
    public List<Application> applications = new ArrayList<>();
    public List<EventType> eventTypes = new ArrayList<>();
    public List<BehaviorGroup> behaviorGroups = new ArrayList<>();
    public List<Endpoint> endpoints = new ArrayList<>();
    public List<Event> events = new ArrayList<>();
    public List<NotificationHistory> notificationHistories = new ArrayList<>();
    public List<UUID> bundleIds = new ArrayList<>();
    public List<UUID> applicationIds = new ArrayList<>();
    public List<UUID> behaviorGroupIds = new ArrayList<>();
    public List<UUID> endpointIds = new ArrayList<>();
}
