package com.redhat.cloud.notifications.recipients;

import com.redhat.cloud.notifications.recipients.rbac.RbacRecipientUsersProvider;
import io.micrometer.core.instrument.MeterRegistry;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
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
            .flatMap(r -> recipientUsers(orgId, r, subscribers, true).stream())
            .collect(Collectors.toSet());
    }

    public Set<User> recipientUsers(String orgId, Set<RecipientSettings> requests, Set<String> subscribers, boolean isOptIn) {
        return requests.stream()
                .flatMap(r -> recipientUsers(orgId, r, subscribers, isOptIn).stream())
                .collect(Collectors.toSet());
    }

    private Set<User> recipientUsers(String orgId, RecipientSettings request, Set<String> subscribers, boolean isOptIn) {
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
            if (isOptIn) {
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
