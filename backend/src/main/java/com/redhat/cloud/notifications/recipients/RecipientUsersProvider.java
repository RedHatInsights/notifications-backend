package com.redhat.cloud.notifications.recipients;

import io.smallrye.mutiny.Uni;

import java.util.List;
import java.util.UUID;

public interface RecipientUsersProvider {

    Uni<List<User>> getUsers(String accountId, boolean adminsOnly);
    Uni<List<User>> getGroupUsers(String accountId, boolean adminsOnly, UUID groupId);

}
