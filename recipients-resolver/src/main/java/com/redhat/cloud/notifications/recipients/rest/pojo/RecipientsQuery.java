package com.redhat.cloud.notifications.recipients.rest.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.redhat.cloud.notifications.recipients.model.ExternalAuthorizationCriteria;
import com.redhat.cloud.notifications.recipients.model.RecipientSettings;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

import static com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;

@JsonNaming(SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class RecipientsQuery {

    @NotBlank
    public String orgId;

    @NotNull
    public Set<RecipientSettings> recipientSettings;

    public Set<String> subscribers;

    public Set<String> unsubscribers;

    public boolean subscribedByDefault;

    public ExternalAuthorizationCriteria externalAuthorizationCriteria;
}
