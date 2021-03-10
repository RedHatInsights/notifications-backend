package com.redhat.cloud.notifications.recipients;

import io.smallrye.mutiny.Multi;

public interface RecipientUsersProvider {

    Multi<User> getUsers(String accountId, boolean adminsOnly);

    Multi<User> getGroupUsers(String accountId, boolean adminsOnly, String groupId);

}
