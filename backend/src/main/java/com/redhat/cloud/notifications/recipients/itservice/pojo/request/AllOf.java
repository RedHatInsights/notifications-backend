package com.redhat.cloud.notifications.recipients.itservice.pojo.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"ebsAccountNumber", "status"})
public class AllOf {

    public AllOf(String ebsAccountNumber, String status) {
        this.ebsAccountNumber = ebsAccountNumber;
        this.status = status;
    }

    @JsonProperty("ebsAccountNumber")
    private String ebsAccountNumber;

    @JsonProperty("status")
    private String status;

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
}
