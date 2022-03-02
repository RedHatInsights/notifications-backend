package com.redhat.cloud.notifications.recipients;

import com.redhat.cloud.notifications.recipients.rbac.RbacRecipientUsersProvider;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class RecipientResolver {

    @Inject
    RbacRecipientUsersProvider rbacRecipientUsersProvider;

    public Set<User> recipientUsers(String accountId, Set<RecipientSettings> requests, Set<String> subscribers) {
        return requests.stream()
                .flatMap(r -> recipientUsers(accountId, r, subscribers).stream())
                .collect(Collectors.toSet());
    }

    private Set<User> recipientUsers(String accountId, RecipientSettings request, Set<String> subscribers) {
        List<User> rbacUsers;
        if (request.getGroupId() == null) {
            rbacUsers = rbacRecipientUsersProvider.getUsers(accountId, request.isOnlyAdmins());
        } else {
            rbacUsers = rbacRecipientUsersProvider.getGroupUsers(accountId, request.isOnlyAdmins(), request.getGroupId());
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
            // Otherwise, the recipients from RBAC who didn't subscribe to the event type are filtered out.
            users = filterUsers(users, subscribers);
        }

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
}
