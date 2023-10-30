package com.redhat.cloud.notifications.connector.email.processors.recipients.pojo;

import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.redhat.cloud.notifications.connector.email.model.settings.RecipientSettings;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import java.util.UUID;

import static com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;

@JsonNaming(SnakeCaseStrategy.class)
public class RecipientsQuery {

    @NotBlank
    public String orgId;

    @NotNull
    public UUID eventTypeId;

    @NotNull
    public String subscriptionType;

    @NotNull
    public Set<RecipientSettings> recipientSettings;
}
