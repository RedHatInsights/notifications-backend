package com.redhat.cloud.notifications.recipients.itservice.pojo.request;

import java.util.LinkedList;
import java.util.List;

public class ITUserRequest {

    public ITUserRequestBy by;
    public Include include;

    public ITUserRequest(String accountId, boolean adminsOnly) {
        final ITUserRequestBy by = new ITUserRequestBy();
        AllOf allOf = new AllOf();
        allOf.status = "enabled";
        allOf.ebsAccountNumber = accountId;

        if (adminsOnly) {
            PermissionCode permissionCode = new PermissionCode();
            permissionCode.value = "admin:org:all";
            permissionCode.operand = "eq";
            allOf.permissionCode = permissionCode;
        }
        by.allOf = allOf;

        this.by = by;
        this.by.withPaging.firstResultIndex = 0;
        this.by.withPaging.maxResults = 10000;

        Include include = new Include();

        include.allOf = List.of("authentications", "personal_information");
        List<AccountRelationship> accountRelationships = new LinkedList<>();
        AccountRelationship accountRelationship1 = new AccountRelationship();
        accountRelationship1.allOf = List.of("primary_email");

        final AccountRelationshipBy by1 = new AccountRelationshipBy();
        by1.active = true;
        accountRelationship1.by = by1;
        accountRelationships.add(accountRelationship1);
        include.accountRelationships = accountRelationships;

        this.include = include;
    }
}
