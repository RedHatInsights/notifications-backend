package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.recipients.rbac.RbacRecipientUsersProvider;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.config.inject.ConfigProperty;

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

    @ConfigProperty(name = "processor.email.rbac-user-query", defaultValue = "false")
    boolean rbacUserQuery;

    public Uni<Set<User>> recipientUsers(String accountId, Set<Endpoint> endpoints, Set<String> subscribers) {

        if (!rbacUserQuery) {
            return Uni.createFrom().item(
                    subscribers.stream().map(username -> {
                        User user = new User();
                        user.setUsername(username);
                        return user;
                    }).collect(Collectors.toSet())
            );
        }

        return Multi.createFrom().iterable(endpoints)
                .onItem().transformToUni(e -> recipientUsers(accountId, e, subscribers))
                .concatenate().collect().in(HashSet<User>::new, Set::addAll);
    }

    private Uni<Set<User>> recipientUsers(String accountId, Endpoint endpoint, Set<String> subscribers) {
        Uni<List<User>> usersUni = rbacRecipientUsersProvider.getUsers(accountId, false);
        return usersUni
                .onItem().transform(users -> users
                        .stream()
                        .filter(user -> subscribers
                                .stream()
                                .anyMatch(subscriber -> subscriber.equalsIgnoreCase(user.getUsername()))
                        )
                        .collect(Collectors.toSet())
                );
    }
}
