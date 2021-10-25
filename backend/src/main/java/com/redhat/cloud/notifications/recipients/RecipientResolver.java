package com.redhat.cloud.notifications.recipients;

import com.redhat.cloud.notifications.recipients.rbac.RbacRecipientUsersProvider;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class RecipientResolver {

    private static final Logger LOGGER = Logger.getLogger(RecipientResolver.class);

    @Inject
    RbacRecipientUsersProvider rbacRecipientUsersProvider;

    public Uni<Set<User>> recipientUsers(String accountId, Set<RecipientSettings> requests, Set<String> subscribers) {
        return Multi.createFrom().iterable(filterOutExtraSettings(requests))
                .onItem().transformToUni(r -> recipientUsers(accountId, r, subscribers))
                .concatenate().collect().in(HashSet::new, Set::addAll);
    }

    // If there is any settings that fetches all users, use it instead of using all.
    private Set<RecipientSettings> filterOutExtraSettings(Set<RecipientSettings> requests) {
        Set<RecipientSettings> allUsersRequest = requests
                .stream()
                .filter(recipientSettings -> !recipientSettings.isOnlyAdmins() && recipientSettings.getGroupId() == null)
                .collect(Collectors.toSet());

        if (allUsersRequest.size() > 0) {
            return allUsersRequest;
        }

        return requests;
    }

    private Uni<Set<User>> recipientUsers(String accountId, RecipientSettings request, Set<String> subscribers) {
        Uni<List<User>> usersUni;
        if (request.getGroupId() == null) {
            usersUni = rbacRecipientUsersProvider.getUsers(accountId, request.isOnlyAdmins());
        } else {
            usersUni = rbacRecipientUsersProvider.getGroupUsers(accountId, request.isOnlyAdmins(), request.getGroupId());
        }

        return usersUni.onItem().transform(users -> {
            if (request.isIgnoreUserPreferences()) {
                return Set.copyOf(users);
            }

            return users.stream()
                    .filter(user -> subscribers
                            .stream()
                            .anyMatch(subscriber -> subscriber.equalsIgnoreCase(user.getUsername()))
                    )
                    .collect(Collectors.toSet());
        });
    }
}
