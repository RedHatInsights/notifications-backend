package com.redhat.cloud.notifications.recipients.itservice.pojo.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedList;
import java.util.List;

public class ITUserRequest {

    @JsonProperty("by")
    public By by;

    @JsonProperty("include")
    public Include include;

    public ITUserRequest(boolean adminsOnly) {
        final By by = new By();
        AllOf allOf = new AllOf();
        allOf.status = "enabled";
        allOf.ebsAccountNumber = "5910538";

        if (adminsOnly) {
            PermissionCode permissionCode = new PermissionCode();
            permissionCode.value = "admin:org:all";
            permissionCode.operand = "eq";
            allOf.permissionCode = permissionCode;
        }
        by.allOf = allOf;

        WithPaging withPaging = new WithPaging();
        withPaging.firstResultIndex = 0;
        withPaging.maxResults = 10000;
        by.withPaging = withPaging;

        this.by = by;

        Include include = new Include();

        include.allOf = List.of("authentications", "personal_information");
        List<AccountRelationship> accountRelationships = new LinkedList<>();
        AccountRelationship accountRelationship1 = new AccountRelationship();
        accountRelationship1.allOf = List.of("primary_email");

        final By__1 by1 = new By__1();
        by1.active = true;
        accountRelationship1.by = by1;
        accountRelationships.add(accountRelationship1);
        include.accountRelationships = accountRelationships;

        this.include = include;
    }

    public By getBy() {
        return by;
    }

    public void setBy(By by) {
        this.by = by;
    }

    public Include getInclude() {
        return include;
    }

    public void setInclude(Include include) {
        this.include = include;
    }
}
