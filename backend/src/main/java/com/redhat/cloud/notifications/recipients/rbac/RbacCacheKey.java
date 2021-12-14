package com.redhat.cloud.notifications.recipients.rbac;

import java.util.Objects;

public class RbacCacheKey {

    private final String accountId;
    private final boolean adminsOnly;

    public RbacCacheKey(String accountId, boolean adminsOnly) {
        this.accountId = accountId;
        this.adminsOnly = adminsOnly;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RbacCacheKey that = (RbacCacheKey) o;
        return adminsOnly == that.adminsOnly && Objects.equals(accountId, that.accountId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, adminsOnly);
    }
}
