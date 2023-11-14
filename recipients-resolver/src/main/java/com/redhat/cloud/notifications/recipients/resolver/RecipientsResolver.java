package com.redhat.cloud.notifications.recipients.resolver;

import com.redhat.cloud.notifications.recipients.model.RecipientSettings;
import com.redhat.cloud.notifications.recipients.model.User;
import io.quarkus.cache.CacheResult;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

@ApplicationScoped
public class RecipientsResolver {

    @Inject
    FetchUsersFromExternalServices fetchingUsers;

    @CacheResult(cacheName = "find-recipients")
    public Set<User> findRecipients(String orgId, Set<RecipientSettings> recipientSettings, Set<String> subscribers, Set<String> unsubscribers, boolean subscribedByDefault) {
        Set<String> lowerCaseSubscribers = toLowerCaseOrEmpty(subscribers);
        Set<String> lowerCaseUnsubscribers = toLowerCaseOrEmpty(unsubscribers);
        return recipientSettings.stream()
            .flatMap(r -> recipientUsers(orgId, r, lowerCaseSubscribers, lowerCaseUnsubscribers, subscribedByDefault).stream())
            .collect(toSet());
    }

    private Set<User> recipientUsers(String orgId, RecipientSettings request, Set<String> subscribers, Set<String> unsubscribers, boolean subscribedByDefault) {

        /*
         * When:
         * - subscribedByDefault is false
         * - the user preferences should NOT be ignored
         * - the subscribers Set is empty
         * Then, we don't need to retrieve the users from the external service because we'll return an empty Set anyway.
         */
        if (!subscribedByDefault && !request.isIgnoreUserPreferences() && subscribers.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> requestUsers = toLowerCaseOrEmpty(request.getUsers());

        List<User> fetchedUsers;
        if (request.getGroupUUID() == null) {
            fetchedUsers = fetchingUsers.getUsers(orgId, request.isAdminsOnly());
        } else {
            fetchedUsers = fetchingUsers.getGroupUsers(orgId, request.isAdminsOnly(), request.getGroupUUID());
        }

        // The fetched users are cached, so we need to create a new Set to avoid altering the cached data.
        Set<User> users = new HashSet<>(fetchedUsers);

        // We need to remove from the users Set the ones that do not qualify as recipients.
        users.removeIf(user -> {
            String lowerCaseUsername = user.getUsername().toLowerCase();

            /*
             * When the request contains a list of users, only these users will qualify as recipients,
             * if we did fetch them from the external service. Any fetched users who are not included
             * in the request are removed.
             */
            if (!requestUsers.isEmpty() && !requestUsers.contains(lowerCaseUsername)) {
                return true;
            }

            // Subscriptions are only considered if the user preferences are NOT ignored.
            if (!request.isIgnoreUserPreferences()) {
                if (subscribedByDefault) {
                    // When subscribedByDefault is true, we need to remove from the users anyone who unsubscribed.
                    return unsubscribers.contains(lowerCaseUsername);
                } else {
                    // When subscribedByDefault is false, we need to keep only subscribed users, by removing from the users anyone who DID NOT subscribe.
                    return !subscribers.contains(lowerCaseUsername);
                }
            }

            // The current user was not removed by any of the conditions above, so we'll keep them in the Set!
            return false;

        });
        Log.infof("%d recipients found for OrgId %s", users.size(), orgId);
        return users;
    }

    private static Set<String> toLowerCaseOrEmpty(Set<String> usernames) {
        if (usernames == null) {
            return Collections.emptySet();
        } else {
            return usernames.stream()
                    .map(String::toLowerCase)
                    .collect(toSet());
        }
    }
}
