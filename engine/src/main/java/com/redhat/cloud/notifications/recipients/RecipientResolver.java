package com.redhat.cloud.notifications.recipients;

import com.redhat.cloud.notifications.recipients.rbac.RbacRecipientUsersProvider;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@ApplicationScoped
public class RecipientResolver {

    private final AtomicInteger usersCount = new AtomicInteger(0);

    @Inject
    RbacRecipientUsersProvider rbacRecipientUsersProvider;

    @Inject
    MeterRegistry meterRegistry;

    @PostConstruct
    public void init() {
        meterRegistry.gauge("email-processor.recipients-resolved", usersCount);
    }

    public Set<User> recipientUsers(String orgId, Set<RecipientSettings> requests, Set<String> subscribers) {
        return requests.stream()
            .flatMap(r -> recipientUsers(orgId, r, subscribers, false).stream())
            .collect(Collectors.toSet());
    }

    public Set<User> recipientUsers(String orgId, Set<RecipientSettings> requests, Set<String> subscribers, boolean subscribedByDefault) {
        return requests.stream()
                .flatMap(r -> recipientUsers(orgId, r, subscribers, subscribedByDefault).stream())
                .collect(Collectors.toSet());
    }

    private Set<User> recipientUsers(String orgId, RecipientSettings request, Set<String> subscribers, boolean subscribedByDefault) {
        /*
         If the subscription type is opt-in, the user preferences should NOT be ignored and the subscribers Set is empty,
         then we don't need to retrieve the users from RBAC/IT/BOP because we'll return an empty Set anyway.
         */
        if (!subscribedByDefault && !request.isIgnoreUserPreferences() && subscribers.isEmpty()) {
            usersCount.set(0);
            return Collections.emptySet();
        }

        List<User> rbacUsers;
        if (request.getGroupId() == null) {
            rbacUsers = rbacRecipientUsersProvider.getUsers(orgId, request.isOnlyAdmins());
        } else {
            rbacUsers = rbacRecipientUsersProvider.getGroupUsers(orgId, request.isOnlyAdmins(), request.getGroupId());
        }

        // The base list of recipients comes from RBAC.
        Set<User> users = Set.copyOf(rbacUsers);

        // If the request contains a list of users, then the recipients from RBAC who are not included in
        // the request users list are filtered out.
        // Otherwise, the full list of recipients from RBAC will be processed by the next step.
        if (request.getUsers().size() > 0) {
            users = filterUsers(users, request.getUsers());
        }

        // If the user preferences should be ignored, the recipients from RBAC (possibly filtered by the
        // previous step) is returned without filtering out the users who didn't subscribe to the event type.
        if (request.isIgnoreUserPreferences()) {
            users = Set.copyOf(users);
        } else {
            if (!subscribedByDefault) {
                // Otherwise, the recipients from RBAC who didn't subscribe to the event type are filtered out.
                users = filterUsers(users, subscribers);
            } else {
                users = filterUnsubscribedUsers(users, subscribers);
            }
        }

        updateUsersUsedGauge(users.size());

        return users;
    }

    private Set<User> filterUsers(Set<User> users, Set<String> target) {
        return users.stream()
                .filter(
                    user -> target
                            .stream()
                            .anyMatch(requested -> requested.equalsIgnoreCase(user.getUsername()))
                )
                .collect(Collectors.toSet());
    }

    private Set<User> filterUnsubscribedUsers(Set<User> users, Set<String> target) {
        return users.stream()
            .filter(
                user -> target
                    .stream()
                    .noneMatch(requested -> requested.equalsIgnoreCase(user.getUsername()))
            )
            .collect(Collectors.toSet());
    }

    private void updateUsersUsedGauge(int users) {
        usersCount.set(users);
    }
}
