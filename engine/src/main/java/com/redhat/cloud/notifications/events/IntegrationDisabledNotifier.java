package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Parser;
import com.redhat.cloud.notifications.ingress.Recipient;
import com.redhat.cloud.notifications.models.Endpoint;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;

import javax.enterprise.context.ApplicationScoped;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.redhat.cloud.notifications.events.FromCamelHistoryFiller.EGRESS_CHANNEL;
import static java.time.ZoneOffset.UTC;

@ApplicationScoped
public class IntegrationDisabledNotifier {

    public static final String CLIENT_ERROR_TYPE = "client";
    public static final String SERVER_ERROR_TYPE = "server";
    public static final String CONSOLE_BUNDLE = "console";
    public static final String INTEGRATIONS_APP = "integrations";
    public static final String INTEGRATION_DISABLED_EVENT_TYPE = "integration-disabled";
    public static final String ERROR_TYPE_PROPERTY = "error_type";
    public static final String ENDPOINT_ID_PROPERTY = "endpoint_id";
    public static final String ENDPOINT_NAME_PROPERTY = "endpoint_name";
    public static final String STATUS_CODE_PROPERTY = "status_code";
    public static final String ERRORS_COUNT_PROPERTY = "errors_count";

    @Channel(EGRESS_CHANNEL)
    Emitter<String> emitter;

    public void clientError(Endpoint endpoint, int statusCode) {
        notify(endpoint, CLIENT_ERROR_TYPE, statusCode, 1);
    }

    public void tooManyServerErrors(Endpoint endpoint, int errorsCount) {
        notify(endpoint, SERVER_ERROR_TYPE, -1, errorsCount);
    }

    private void notify(Endpoint endpoint, String errorType, int statusCode, int errorsCount) {
        Action action = buildIntegrationDisabledAction(endpoint, errorType, statusCode, errorsCount);
        String encodedAction = Parser.encode(action);
        Message<String> message = KafkaMessageWithIdBuilder.build(encodedAction);
        emitter.send(message);
    }

    public static Action buildIntegrationDisabledAction(Endpoint endpoint, String errorType, int statusCode, int errorsCount) {
        Context.ContextBuilderBase contextBuilder = new Context.ContextBuilder()
                .withAdditionalProperty(ERROR_TYPE_PROPERTY, errorType)
                .withAdditionalProperty(ENDPOINT_ID_PROPERTY, endpoint.getId())
                .withAdditionalProperty(ENDPOINT_NAME_PROPERTY, endpoint.getName())
                .withAdditionalProperty(ERRORS_COUNT_PROPERTY, errorsCount);

        if (statusCode > 0) {
            contextBuilder.withAdditionalProperty(STATUS_CODE_PROPERTY, statusCode);
        }

        Recipient recipients = new Recipient.RecipientBuilder()
                .withOnlyAdmins(true)
                .withIgnoreUserPreferences(true)
                .build();

        return new Action.ActionBuilder()
                .withId(UUID.randomUUID())
                .withBundle(CONSOLE_BUNDLE)
                .withApplication(INTEGRATIONS_APP)
                .withEventType(INTEGRATION_DISABLED_EVENT_TYPE)
                .withOrgId(endpoint.getOrgId())
                .withTimestamp(LocalDateTime.now(UTC))
                .withContext(contextBuilder.build())
                .withRecipients(List.of(recipients))
                .build();
    }
}
