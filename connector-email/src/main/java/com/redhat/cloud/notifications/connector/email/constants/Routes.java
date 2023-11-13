package com.redhat.cloud.notifications.connector.email.constants;

public class Routes {
    public static final String FETCH_GROUP = "fetch-group-rbac";
    public static final String FETCH_GROUP_USERS = "fetch-group-rbac-users";
    public static final String FETCH_USERS = "fetch-users";
    public static final String FETCH_USERS_IT = "fetch-users-it";
    public static final String FETCH_USERS_RBAC = "fetch-users-rbac";
    public static final String SEND_EMAIL_BOP = "send-email-bop";
    @Deprecated(forRemoval = true)
    public static final String RESOLVE_USERS_WITHOUT_RECIPIENTS_RESOLVER_CLOWDAPP = "fetch-users-without-recipients-resolver-clowdapp";

    public static final String RESOLVE_USERS_WITH_RECIPIENTS_RESOLVER_CLOWDAPP = "fetch-users-with-recipients-resolver-clowdapp";

}
