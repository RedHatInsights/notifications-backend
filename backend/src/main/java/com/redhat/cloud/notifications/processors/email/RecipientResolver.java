package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.models.EmailSubscriptionProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.recipients.User;
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

    public Uni<Set<User>> recipientUsers(String accountId, Set<Endpoint> endpoints, Set<String> subscribers) {
        return Multi.createFrom().iterable(endpoints)
                .onItem().transformToUni(e -> recipientUsers(accountId, e, subscribers))
                .concatenate().collect().in(HashSet::new, Set::addAll);
    }

    private Uni<Set<User>> recipientUsers(String accountId, Endpoint endpoint, Set<String> subscribers) {
        final EmailSubscriptionProperties props = endpoint.getProperties(EmailSubscriptionProperties.class);

        Uni<List<User>> usersUni;
        if (props.getGroupId() == null) {
            usersUni = rbacRecipientUsersProvider.getUsers(accountId, props.isOnlyAdmins());
        } else {
            usersUni = rbacRecipientUsersProvider.getGroupUsers(accountId, props.isOnlyAdmins(), props.getGroupId());
        }

        return usersUni.onItem().transform(users -> {
            if (props.isIgnorePreferences()) {
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
