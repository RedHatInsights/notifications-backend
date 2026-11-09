package com.redhat.cloud.notifications.routers.internal.userpreferencesmigration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.auth.ConsoleIdentityProvider;
import com.redhat.cloud.notifications.db.repositories.ApplicationServicesMigrationRepository;
import com.redhat.cloud.notifications.models.EventType;
import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestForm;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Path(Constants.API_INTERNAL + "/application-services")
@RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
public class ApplicationServicesUserPreferencesMigrationResource {

    @Path(Constants.API_INTERNAL_NOTIFICATIONS_BACKEND + "/application-services")
    @RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
    public static class V1 extends ApplicationServicesUserPreferencesMigrationResource {
    }

    @Inject
    ApplicationServicesMigrationRepository applicationServicesMigrationRepository;

    @Inject
    ObjectMapper objectMapper;

    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Path("/migrate/json")
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    @Transactional
    public void migrateApplicationServicesUserPreferencesJSON(@NotNull @RestForm("jsonFile") InputStream jsonFile) {
        Log.info("Start migrateApplicationServicesUserPreferencesJSON");

        final List<EventType> applicationServicesEventTypes = applicationServicesMigrationRepository.findApplicationServicesEventTypes();
        final Map<String, EventType> mappedEventTypes = applicationServicesEventTypes
            .stream()
            .collect(
                Collectors.toMap(
                    EventType::getName,
                    Function.identity()
                )
            );

        List<ApplicationServicesSubscriptionInput> inputs;
        try {
            inputs = objectMapper.readValue(
                jsonFile.readAllBytes(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, ApplicationServicesSubscriptionInput.class)
            );
        } catch (final IOException e) {
            Log.error("Failed to parse JSON migration file", e);
            throw new BadRequestException("Invalid JSON format in migration file");
        }

        long totalInsertedElements = 0L;
        Map<String, Long> skippedByEventType = new HashMap<>();
        for (final ApplicationServicesSubscriptionInput input : inputs) {
            final EventType eventType = mappedEventTypes.get(input.eventType());
            if (eventType == null) {
                Log.warnf("Unknown event type \"%s\" for user \"%s\" (org %s), skipping", input.eventType(), input.username(), input.orgId());
                skippedByEventType.merge(input.eventType(), 1L, Long::sum);
                continue;
            }

            applicationServicesMigrationRepository.saveApplicationServicesSubscription(input.username(), input.orgId(), eventType);
            totalInsertedElements++;
        }

        if (!skippedByEventType.isEmpty()) {
            Log.warnf("Skipped records by unknown event type: %s", skippedByEventType);
        }
        Log.infof("A total of %d Application Services subscriptions were persisted in the database, %d records skipped", totalInsertedElements, skippedByEventType.values().stream().mapToLong(Long::longValue).sum());
    }
}
