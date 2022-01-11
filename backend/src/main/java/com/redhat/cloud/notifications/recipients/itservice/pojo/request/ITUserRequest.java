package com.redhat.cloud.notifications.recipients.itservice.pojo.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedList;
import java.util.List;

public class ITUserRequest {

    @JsonProperty("by")
    private By by;

    @JsonProperty("include")
    private Include include;

    public ITUserRequest(boolean adminsOnly) {
        final By by = new By();
        AllOf allOf = new AllOf();
        allOf.setStatus("enabled");
        allOf.setEbsAccountNumber("5910538");

        if (adminsOnly) {
            PermissionCode permissionCode = new PermissionCode();
            permissionCode.setValue("admin:org:all");
            permissionCode.setOperand("eq");
            allOf.setPermissionCode(permissionCode);
        }
        by.setAllOf(allOf);

        WithPaging withPaging = new WithPaging();
        withPaging.setFirstResultIndex(0);
        withPaging.setMaxResults(10000);
        by.setWithPaging(withPaging);

        this.by = by;

        Include include = new Include();

        include.setAllOf(List.of("authentications", "personal_information"));
        List<AccountRelationship> accountRelationships = new LinkedList<>();
        AccountRelationship accountRelationship1 = new AccountRelationship();
        accountRelationship1.setAllOf(List.of("primary_email"));

        final By__1 by1 = new By__1();
        by1.setActive(true);
        accountRelationship1.setBy(by1);
        accountRelationships.add(accountRelationship1);
        include.setAccountRelationships(accountRelationships);

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
