package com.redhat.cloud.notifications.recipients.itservice.pojo.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "id",
        "authentications",
        "accountRelationships",
        "personalInformation"
})
public class ITUserResponse {

    @JsonProperty("id")
    private String id;

    @JsonProperty("authentications")
    private List<Authentication> authentications;

    @JsonProperty("accountRelationships")
    private List<AccountRelationship> accountRelationships;

    @JsonProperty("personalInformation")
    private PersonalInformation personalInformation;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<Authentication> getAuthentications() {
        return authentications;
    }

    public void setAuthentications(List<Authentication> authentications) {
        this.authentications = authentications;
    }

    public List<AccountRelationship> getAccountRelationships() {
        return accountRelationships;
    }

    public void setAccountRelationships(List<AccountRelationship> accountRelationships) {
        this.accountRelationships = accountRelationships;
    }

    public PersonalInformation getPersonalInformation() {
        return personalInformation;
    }

    public void setPersonalInformation(PersonalInformation personalInformation) {
        this.personalInformation = personalInformation;
    }
}
