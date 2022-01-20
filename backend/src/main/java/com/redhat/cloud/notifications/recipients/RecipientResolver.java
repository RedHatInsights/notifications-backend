package com.redhat.cloud.notifications.recipients;

import com.redhat.cloud.notifications.recipients.rbac.RbacRecipientUsersProvider;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class RecipientResolver {

    @Inject
    RbacRecipientUsersProvider rbacRecipientUsersProvider;

    public Uni<Set<User>> recipientUsers(String accountId, Set<RecipientSettings> requests, Set<String> subscribers) {
        return Multi.createFrom().iterable(requests)
                .onItem().transformToUni(r -> recipientUsers(accountId, r, subscribers))
                .concatenate().collect().in(HashSet::new, Set::addAll);
    }

    private Uni<Set<User>> recipientUsers(String accountId, RecipientSettings request, Set<String> subscribers) {
        Uni<List<User>> usersUni;
        if (request.getGroupId() == null) {
            usersUni = rbacRecipientUsersProvider.getUsers(accountId, request.isOnlyAdmins());
        } else {
            usersUni = rbacRecipientUsersProvider.getGroupUsers(accountId, request.isOnlyAdmins(), request.getGroupId());
        }

        // The base list of recipients comes from RBAC.
        return usersUni
                .onItem().transform(Set::copyOf)
                .onItem().transform(users -> {
                    // If the request contains a list of users, then the recipients from RBAC who are not included in
                    // the request users list are filtered out.
                    if (request.getUsers().size() > 0) {
                        return filterUsers(users, request.getUsers());
                    }
                    // Otherwise, the full list of recipients from RBAC will be processed by the next step.
                    return users;
                }).onItem().transform(users -> {
                    // If the user preferences should be ignored, the recipients from RBAC (possibly filtered by the
                    // previous step) is returned without filtering out the users who didn't subscribe to the event type.
                    if (request.isIgnoreUserPreferences()) {
                        return Set.copyOf(users);
                    }
                    // Otherwise, the recipients from RBAC who didn't subscribe to the event type are filtered out.
                    return filterUsers(users, subscribers);
                });
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
