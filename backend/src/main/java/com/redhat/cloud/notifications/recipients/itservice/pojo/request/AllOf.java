package com.redhat.cloud.notifications.recipients.itservice.pojo.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "ebsAccountNumber",
        "status",
        "permissionCode"
})
public class AllOf {

    @JsonProperty("ebsAccountNumber")
    private String ebsAccountNumber;

    @JsonProperty("status")
    private String status;

    @JsonProperty("permissionCode")
    private PermissionCode permissionCode;

    public String getEbsAccountNumber() {
        return ebsAccountNumber;
    }

    public void setEbsAccountNumber(String ebsAccountNumber) {
        this.ebsAccountNumber = ebsAccountNumber;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public PermissionCode getPermissionCode() {
        return permissionCode;
    }

    public void setPermissionCode(PermissionCode permissionCode) {
        this.permissionCode = permissionCode;
    }
}
