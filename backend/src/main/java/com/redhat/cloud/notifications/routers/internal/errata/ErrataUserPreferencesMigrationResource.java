package com.redhat.cloud.notifications.routers.internal.errata;

import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.auth.ConsoleIdentityProvider;
import com.redhat.cloud.notifications.db.repositories.ErrataMigrationRepository;
import com.redhat.cloud.notifications.models.EventType;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestForm;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Path(Constants.API_INTERNAL + "/team-nado")
@RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
public class ErrataUserPreferencesMigrationResource {
    public static final String EVENT_TYPE_NAME_BUGFIX = "new-subscription-bugfix-errata";
    public static final String EVENT_TYPE_NAME_ENHANCEMENT = "new-subscription-enhancement-errata";
    public static final String EVENT_TYPE_NAME_SECURITY = "new-subscription-security-errata";
    private static final String JSON_KEY_ORG_ID = "org_id";
    private static final String JSON_KEY_SUBSCRIPTION_PREFERENCES = "preferences";
    private static final String JSON_KEY_USERNAME = "username";
    private static final String JSON_VALUE_PREFERENCE_BUGFIX = "bugfix";
    private static final String JSON_VALUE_PREFERENCE_ENHANCEMENT = "enhancement";
    private static final String JSON_VALUE_PREFERENCE_SECURITY = "security";

    @Inject
    ErrataMigrationRepository errataMigrationRepository;

    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Path("/migrate/json")
    @POST
    public void migrateTeamNadoUserPreferencesJSON(@NotNull @RestForm("jsonFile") InputStream jsonFile) throws IOException {
        final byte[] contents = jsonFile.readAllBytes();

        // Match the event types to the preference values that we will find
        // in the JSON file.
        final List<EventType> errataEventTypes = this.errataMigrationRepository.findErrataEventTypes();
        final Map<String, EventType> mappedEventTypes = errataEventTypes
            .stream()
            .collect(
                Collectors.toMap(
                    et -> switch (et.getName()) {
                        case EVENT_TYPE_NAME_BUGFIX -> JSON_VALUE_PREFERENCE_BUGFIX;
                        case EVENT_TYPE_NAME_ENHANCEMENT -> JSON_VALUE_PREFERENCE_ENHANCEMENT;
                        case EVENT_TYPE_NAME_SECURITY -> JSON_VALUE_PREFERENCE_SECURITY;
                        default -> {
                            Log.errorf("Unable to map event type \"%s\" to any expected preference values that we expect to find in the JSON", et.getName());

                            throw new InternalServerErrorException("A non errata event type was detected. Nothing was executed.");
                        }
                    },
                    // ... to our event types.
                    Function.identity()
                )
            );

        // Extract the subscriptions from the JSON file.
        final String stringContents = new String(contents, StandardCharsets.UTF_8);
        final JsonArray jsonArray = new JsonArray(stringContents);

        final List<ErrataSubscription> errataSubscriptions = new ArrayList<>();
        for (final Object rawJsonObject : jsonArray) {
            final JsonObject teamNadoSubscription = (JsonObject) rawJsonObject;

            final JsonArray eventTypeSubscriptions = teamNadoSubscription.getJsonArray(JSON_KEY_SUBSCRIPTION_PREFERENCES);
            final Set<EventType> subscribedEventTypes = new HashSet<>();
            if (null != eventTypeSubscriptions) {
                eventTypeSubscriptions.forEach(sub -> {
                    subscribedEventTypes.add(
                        mappedEventTypes.get((String) sub)
                    );
                });
            }

            errataSubscriptions.add(new ErrataSubscription(teamNadoSubscription.getString(JSON_KEY_USERNAME), teamNadoSubscription.getString(JSON_KEY_ORG_ID), subscribedEventTypes));
        }

        Log.infof("Read JSON errata subscriptions file and extracted %s subscriptions from it", errataSubscriptions.size());

        // Persist the errata subscriptions in the database.
        try {
            this.errataMigrationRepository.saveErrataSubscriptions(errataSubscriptions);
        } catch (final Exception e) {
            Log.error("Unable to migrate errata subscriptions due to an exception. The insertions were rolled back", e);
        }
    }
}
