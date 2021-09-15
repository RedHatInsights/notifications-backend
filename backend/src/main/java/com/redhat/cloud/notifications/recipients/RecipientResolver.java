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

    public Uni<Set<User>> recipientUsers(String accountId, Set<RecipientResolverRequest> requests, Set<String> subscribers) {
        return Multi.createFrom().iterable(requests)
                .onItem().transformToUni(r -> recipientUsers(accountId, r, subscribers))
                .concatenate().collect().in(HashSet::new, Set::addAll);
    }

    private Uni<Set<User>> recipientUsers(String accountId, RecipientResolverRequest request, Set<String> subscribers) {
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
