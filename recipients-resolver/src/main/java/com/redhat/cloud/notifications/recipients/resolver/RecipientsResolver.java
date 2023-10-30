package com.redhat.cloud.notifications.recipients.resolver;

import com.redhat.cloud.notifications.recipients.SubscriptionRepository;
import com.redhat.cloud.notifications.recipients.SubscriptionType;
import com.redhat.cloud.notifications.recipients.model.RecipientSettings;
import com.redhat.cloud.notifications.recipients.model.User;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static java.util.stream.Collectors.toSet;

@ApplicationScoped
public class RecipientsResolver {

    @Inject
    SubscriptionRepository subscriptionRepository;

    @Inject
    FetchUsersFromExternalServices fetchingUsers;

    @CacheResult(cacheName = "find-recipients")
    public List<User> findRecipients(String orgId, UUID eventTypeId, SubscriptionType subscriptionType, Set<RecipientSettings> recipientSettings) {

        boolean subscribedByDefault = subscriptionType.isSubscribedByDefault() || subscriptionRepository.isEventTypeSubscribedByDefault(eventTypeId);

        Set<String> subscribers;
        Set<String> unsubscribers;
        if (subscribedByDefault) {
            subscribers = Collections.emptySet();
            unsubscribers = subscriptionRepository.getUnsubscribers(orgId, eventTypeId, subscriptionType);
        } else {
            subscribers = subscriptionRepository.getSubscribers(orgId, eventTypeId, subscriptionType);
            unsubscribers = Collections.emptySet();
        }

        return recipientSettings.stream()
            .flatMap(r -> recipientUsers(orgId, r, subscribers, unsubscribers, subscribedByDefault).stream())
            .distinct()
            .toList();
    }

    private Set<User> recipientUsers(String orgId, RecipientSettings request, Set<String> subscribers, Set<String> unsubscribers, boolean subscribedByDefault) {

         /*
         If the subscription type is opt-in, the user preferences should NOT be ignored and the subscribers Set is empty,
         then we don't need to retrieve the users from RBAC/IT/BOP because we'll return an empty Set anyway.
         */
        if (!subscribedByDefault && !request.isIgnoreUserPreferences() && subscribers.isEmpty()) {
            return Collections.emptySet();
        }

        List<User> rbacUsers;
        if (request.getGroupUUID() == null) {
            rbacUsers = fetchingUsers.getUsers(orgId, request.isAdminsOnly());
        } else {
            rbacUsers = fetchingUsers.getGroupUsers(orgId, request.isAdminsOnly(), request.getGroupUUID());
        }

        // The base list of recipients comes from RBAC.
        Set<User> users = new HashSet<>(rbacUsers);

        users.removeIf(user -> {

            // If the request contains a list of users, then the recipients from RBAC who are not included in
            // the request users list are filtered out.
            // Otherwise, the full list of recipients from RBAC will be processed by the next step.
            if (!request.getUsers().isEmpty() && !containsIgnoreCase(request.getUsers(), user.getUsername())) {
                return true;
            }

            if (!request.isIgnoreUserPreferences()) {
                if (subscribedByDefault) {
                    if (containsIgnoreCase(unsubscribers, user.getUsername())) {
                        return true;
                    }
                } else {
                    if (!containsIgnoreCase(subscribers, user.getUsername())) {
                        return true;
                    }
                }
            }

            return false;

        });

        return users;
    }

    private static boolean containsIgnoreCase(Set<String> usernames, String username) {
        if (usernames == null || usernames.isEmpty()) {
            return false;
        } else {
            return usernames.stream()
                    .map(String::toLowerCase)
                    .collect(toSet())
                    .contains(username.toLowerCase());
        }
    }
}
