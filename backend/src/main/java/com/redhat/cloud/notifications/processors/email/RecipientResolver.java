package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.recipients.rbac.RbacRecipientUsersProvider;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class RecipientResolver {

    @Inject
    RbacRecipientUsersProvider rbacRecipientUsersProvider;

    public Uni<List<User>> recipientUsers(String accountId, List<Endpoint> endpoints, Set<String> subscribers) {
        return Multi.createFrom().iterable(endpoints)
                .onItem().transformToUni(e -> recipientUsers(accountId, e, subscribers))
                .concatenate().collect().in(ArrayList<User>::new, List::addAll)
                .onItem().transform(users -> users.stream().distinct().collect(Collectors.toList()));
    }

    private Uni<List<User>> recipientUsers(String accountId, Endpoint endpoint, Set<String> subscribers) {
        Uni<List<User>> usersUni = rbacRecipientUsersProvider.getUsers(accountId, false);
        return usersUni.onItem().transform(users -> users.stream().filter(user -> subscribers.contains(user.getUsername())).collect(Collectors.toList()));
    }

}
