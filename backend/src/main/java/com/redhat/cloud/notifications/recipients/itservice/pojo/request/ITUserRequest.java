package com.redhat.cloud.notifications.recipients.itservice.pojo.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.LinkedList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "by",
        "include"
})
public class ITUserRequest {

    @JsonProperty("by")
    private By by;

    @JsonProperty("include")
    private Include include;

    public ITUserRequest() {
        final By by = new By();
        AllOf allOf = new AllOf();
        allOf.setStatus("enabled");
        allOf.setEbsAccountNumber("5910538");
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
