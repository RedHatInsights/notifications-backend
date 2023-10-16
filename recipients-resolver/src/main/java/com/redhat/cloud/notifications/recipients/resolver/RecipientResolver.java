package com.redhat.cloud.notifications.recipientresolver.resolver;


import com.redhat.cloud.notifications.recipientresolver.model.RecipientSettings;
import com.redhat.cloud.notifications.recipientresolver.model.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;



@ApplicationScoped
public class RecipientResolver {

    @Inject
    FetchUsersFromExternalServices fetchingUsers;


    public List<User> findRecipients(String orgId, Set<RecipientSettings> recipientSettings, Set<String> subscribers, boolean isOptIn) {
        return recipientSettings.stream()
            .flatMap(r -> recipientUsers(orgId, r, subscribers, isOptIn).stream())
            .collect(Collectors.toSet()).stream().toList();
    }

    private Set<User> recipientUsers(String orgId, RecipientSettings request, Set<String> subscribers, boolean isOptIn) {

         /*
         If the subscription type is opt-in, the user preferences should NOT be ignored and the subscribers Set is empty,
         then we don't need to retrieve the users from RBAC/IT/BOP because we'll return an empty Set anyway.
         */
        if (isOptIn && !request.isIgnoreUserPreferences() && subscribers.isEmpty()) {
            return Collections.emptySet();
        }

        List<User> rbacUsers;
        if (request.getGroupUUID() == null) {
            rbacUsers = fetchingUsers.getUsers(orgId, request.isAdminsOnly());
        } else {
            rbacUsers = fetchingUsers.getGroupUsers(orgId, request.isAdminsOnly(), request.getGroupUUID());
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

}
