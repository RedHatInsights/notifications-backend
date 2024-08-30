package com.redhat.cloud.notifications.routers.internal.errata;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.auth.ConsoleIdentityProvider;
import com.redhat.cloud.notifications.db.repositories.ErrataMigrationRepository;
import com.redhat.cloud.notifications.models.EventType;
import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import org.hibernate.StatelessSession;
import org.hibernate.Transaction;
import org.hibernate.resource.transaction.spi.TransactionStatus;
import org.jboss.resteasy.reactive.RestForm;

import java.io.IOException;
import java.io.InputStream;
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
    private static final int BATCH_SIZE = 1000;
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

    @Inject
    ObjectMapper objectMapper;

    @Inject
    StatelessSession statelessSession;

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

        // Begin a transaction for the whole operation.
        final Transaction transaction = this.statelessSession.beginTransaction();
        transaction.begin();

        // Collect the user preferences in batches from the JSON file, so that
        // we don't use excessive memory when processing it.
        long totalInsertedElements = 0L;
        try (JsonParser jsonParser = this.objectMapper.createParser(contents)) {
            if (JsonToken.START_ARRAY != jsonParser.nextToken()) {
                throw new BadRequestException("array of objects expected");
            }

            final List<ErrataSubscription> errataSubscriptions = new ArrayList<>(BATCH_SIZE);
            while (JsonToken.START_OBJECT == jsonParser.nextToken()) {
                final ObjectNode node = this.objectMapper.readTree(jsonParser);

                // Extract the top level elements.
                final String username = node.get(JSON_KEY_USERNAME).asText();
                final String orgId = node.get(JSON_KEY_ORG_ID).asText();
                final JsonNode preferences = node.get(JSON_KEY_SUBSCRIPTION_PREFERENCES);

                // Extract the user preferences.
                final Set<EventType> subscribedEventTypes = new HashSet<>();
                if (preferences != null && preferences.isArray()) {
                    for (final JsonNode preference : preferences) {
                        final String userPreference = preference.asText();

                        subscribedEventTypes.add(
                            mappedEventTypes.get(userPreference)
                        );
                    }
                }

                // Add it to the collection of subscriptions we want to
                // persist.
                errataSubscriptions.add(new ErrataSubscription(username, orgId, subscribedEventTypes));

                // Once we have a consdierable batch of elements to persist,
                // go ahead with it.
                if (errataSubscriptions.size() >= BATCH_SIZE) {
                    Log.infof("Inserting a batch of %s Errata subscriptions into the database", BATCH_SIZE);

                    try {
                        this.errataMigrationRepository.saveErrataSubscriptions(errataSubscriptions);

                        Log.infof("Persisted %s errata subscriptions to the database.", errataSubscriptions.size());

                        // Clear the inserted subscriptions.
                        totalInsertedElements += errataSubscriptions.size();
                        errataSubscriptions.clear();
                    } catch (final Exception e) {
                        if (TransactionStatus.ROLLED_BACK != transaction.getStatus()) {
                            transaction.rollback();
                        }

                        Log.error("Unable to migrate errata subscriptions due to an exception. The insertions were rolled back", e);

                        throw new InternalServerErrorException("Unable to persist subscriptions. The operation was rolled back");
                    }
                }
            }

            // In case we finished parsing the JSON file but there are still
            // some elements not persisted, we need to store those too.
            if (!errataSubscriptions.isEmpty()) {
                try {
                    this.errataMigrationRepository.saveErrataSubscriptions(errataSubscriptions);

                    Log.infof("Persisted %s errata subscriptions to the database.", errataSubscriptions.size());

                    totalInsertedElements += errataSubscriptions.size();
                    errataSubscriptions.clear();
                } catch (final Exception e) {
                    if (TransactionStatus.ROLLED_BACK != transaction.getStatus()) {
                        transaction.rollback();
                    }

                    Log.error("Unable to migrate errata subscriptions due to an exception. The insertions were rolled back", e);

                    throw new InternalServerErrorException("Unable to persist subscriptions. The operation was rolled back");
                }
            }
        }

        // Commit the changes to the database and finalize the operation.
        transaction.commit();

        Log.infof("A total of %d Errata subscriptions were persisted in the database", totalInsertedElements);
    }
}
