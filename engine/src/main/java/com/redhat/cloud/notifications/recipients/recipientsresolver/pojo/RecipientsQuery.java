package com.redhat.cloud.notifications.recipients.recipientsresolver.pojo;

import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.redhat.cloud.notifications.processors.email.connector.dto.RecipientSettings;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

import static com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;

@JsonNaming(SnakeCaseStrategy.class)
public class RecipientsQuery {

    @NotBlank
    public String orgId;

    @NotNull
    public Set<RecipientSettings> recipientSettings;

    public Set<String> subscribers;

    public boolean subscribedByDefault;
}
