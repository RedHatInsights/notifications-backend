package com.redhat.cloud.notifications.connector.microsoft.teams;

import com.redhat.cloud.notifications.connector.microsoft.teams.config.TeamsConnectorConfig;
import com.redhat.cloud.notifications.connector.v2.MessageHandler;
import com.redhat.cloud.notifications.connector.v2.http.models.HandledHttpMessageDetails;
import com.redhat.cloud.notifications.connector.v2.models.HandledMessageDetails;
import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import io.smallrye.reactive.messaging.ce.IncomingCloudEventMetadata;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class TeamsMessageHandler extends MessageHandler {

    public static final String JSON_UTF8 = "application/json; charset=utf-8";
    public static final String BUNDLE = "bundle";
    public static final String APPLICATION = "application";
    public static final String EVENT_TYPE = "event_type";

    @Inject
    TemplateService templateService;

    @Inject
    TeamsConnectorConfig connectorConfig;

    @Inject
    @RestClient
    TeamsRestClient webhookRestClient;

    @Inject
    Validator validator;

    @Override
    public HandledMessageDetails handle(final IncomingCloudEventMetadata<JsonObject> incomingCloudEvent) {
        TeamsNotification notification = incomingCloudEvent.getData().mapTo(TeamsNotification.class);

        Set<ConstraintViolation<TeamsNotification>> violations = validator.validate(notification);
        if (!violations.isEmpty()) {
            String errorMessage = violations.stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining(", "));
            throw new ConstraintViolationException("Validation failed: " + errorMessage, violations);
        }

        String bundle = null;
        String application = null;
        String eventType = null;
        if (null != notification.eventData.get(BUNDLE)) {
            bundle = notification.eventData.get(BUNDLE).toString();
        }
        if (null != notification.eventData.get(APPLICATION)) {
            application = notification.eventData.get(APPLICATION).toString();
        }
        if (null != notification.eventData.get(EVENT_TYPE)) {
            eventType = notification.eventData.get(EVENT_TYPE).toString();
        }

        boolean useBetaTemplate = connectorConfig.isUseBetaTemplatesEnabled(notification.getOrgId(), bundle, application, eventType);

        TemplateDefinition templateDefinition = new TemplateDefinition(
            IntegrationType.MS_TEAMS,
            bundle,
            application,
            eventType,
            useBetaTemplate);
        final String payload = templateService.renderTemplate(templateDefinition, notification.eventData);

        HandledHttpMessageDetails handledMessageDetails = new HandledHttpMessageDetails();
        handledMessageDetails.targetUrl = notification.webhookUrl;

        try (Response response = webhookRestClient.post(notification.webhookUrl, payload)) {
            handledMessageDetails.httpStatus = response.getStatus();
        }

        return handledMessageDetails;
    }
}
