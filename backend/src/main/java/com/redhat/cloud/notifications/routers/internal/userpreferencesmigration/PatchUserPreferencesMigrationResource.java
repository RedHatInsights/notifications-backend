package com.redhat.cloud.notifications.routers.internal.userpreferencesmigration;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.auth.ConsoleIdentityProvider;
import com.redhat.cloud.notifications.db.repositories.ApplicationRepository;
import com.redhat.cloud.notifications.db.repositories.BehaviorGroupRepository;
import com.redhat.cloud.notifications.db.repositories.EndpointEventTypeRepository;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.db.repositories.EventTypeRepository;
import com.redhat.cloud.notifications.db.repositories.SubscriptionRepository;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.SystemSubscriptionProperties;
import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestForm;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.redhat.cloud.notifications.models.SubscriptionType.DAILY;
import static com.redhat.cloud.notifications.models.SubscriptionType.INSTANT;

@Path(Constants.API_INTERNAL + "/patch")
@RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
public class PatchUserPreferencesMigrationResource {
    private static final String JSON_KEY_ORG_ID = "org_id";
    private static final String JSON_KEY_ACCOUNT_ID = "account_id";
    private static final String JSON_KEY_SUBSCRIPTION_PREFERENCES = "preferences";
    private static final String JSON_KEY_USERNAME = "user_name";
    public static final String PATCH_NEW_ADVISORY_EVENT_TYPE = "new-advisory";

    @Inject
    ObjectMapper objectMapper;

    @Inject
    EventTypeRepository eventTypeRepository;

    @Inject
    ApplicationRepository applicationRepository;

    @Inject
    SubscriptionRepository subscriptionRepository;

    @Inject
    EndpointRepository endpointRepository;

    @Inject
    BehaviorGroupRepository behaviorGroupRepository;

    @Inject
    EndpointEventTypeRepository endpointEventTypeRepository;

    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Path("/migrate/json")
    @POST
    @Transactional
    public void migratePatchUserPreferencesJSON(@NotNull @RestForm("jsonFile") InputStream jsonFile) throws IOException {
        final byte[] contents = jsonFile.readAllBytes();

        // Match the event types to the preference values that we will find
        // in the JSON file.
        Application patchApp = applicationRepository.getApplication("rhel", "patch");
        if (patchApp == null) {
            throw new InternalServerErrorException("Patch application does not exist");
        }
        Optional<EventType> patchNewAdvisoryEventType = eventTypeRepository.find(patchApp.getId(), PATCH_NEW_ADVISORY_EVENT_TYPE);

        if (patchNewAdvisoryEventType.isEmpty()) {
            throw new InternalServerErrorException("Patch '" + PATCH_NEW_ADVISORY_EVENT_TYPE + "' event type does not exist");
        }

        Map<String, List<String>> mapSubscribersByOrg  = subscriptionRepository.getEmailSubscribersByEventType(patchNewAdvisoryEventType.get().getId());

        long totalInsertedElements = 0L;
        long totalInsertedIntegrationElements = 0L;
        try (JsonParser jsonParser = objectMapper.createParser(contents)) {
            if (JsonToken.START_ARRAY != jsonParser.nextToken()) {
                throw new BadRequestException("array of objects expected");
            }

            final Set<String> alreadyProcessedOrgIds = new HashSet<>();

            while (JsonToken.START_OBJECT == jsonParser.nextToken()) {
                final ObjectNode node = objectMapper.readTree(jsonParser);

                // Extract the top level elements.
                final String username = node.get(JSON_KEY_USERNAME).asText();
                final String orgId = node.get(JSON_KEY_ORG_ID).asText();
                final String accountId = node.get(JSON_KEY_ACCOUNT_ID).asText();
                final JsonNode preferences = node.get(JSON_KEY_SUBSCRIPTION_PREFERENCES);

                // check if user didn't already set preferences in notifications
                if (mapSubscribersByOrg.containsKey(orgId) && mapSubscribersByOrg.get(orgId).contains(username)) {
                    Log.infof("User %s from org %s already subscribed to patch, skip", username, orgId);
                } else {
                    // Extract the user preferences.
                    boolean subscriptionUpdated = false;
                    if (preferences != null && preferences.isArray()) {
                        for (final JsonNode preference : preferences) {
                            final String userPreference = preference.asText();
                            if (userPreference.equalsIgnoreCase(INSTANT.name())) {
                                subscriptionRepository.subscribe(orgId, username, patchNewAdvisoryEventType.get().getId(), INSTANT);
                                subscriptionUpdated = true;
                                totalInsertedElements++;
                            } else if (userPreference.equalsIgnoreCase(DAILY.name())) {
                                subscriptionRepository.subscribe(orgId, username, patchNewAdvisoryEventType.get().getId(), DAILY);
                                subscriptionUpdated = true;
                                totalInsertedElements++;
                            } else {
                                Log.error("Invalid subscription preference: " + userPreference);
                            }
                        }
                    }

                    if (subscriptionUpdated && !alreadyProcessedOrgIds.contains(orgId)) {
                        List<Endpoint> endpointsLinkedToPatchEventType = endpointEventTypeRepository.findEndpointsByEventTypeId(orgId, patchNewAdvisoryEventType.get().getId(), null, Optional.empty());
                        // Create and email endpoint + event type association if necessary
                        if (endpointsLinkedToPatchEventType.isEmpty() || endpointsLinkedToPatchEventType.stream().noneMatch(ep -> EndpointType.EMAIL_SUBSCRIPTION == ep.getType())) {
                            final Endpoint endpoint = getOrCreateSystemSubscriptionEndpoint(orgId, accountId);
                            endpointEventTypeRepository.addEventTypeToEndpoint(patchNewAdvisoryEventType.get().getId(), endpoint.getId(), orgId);

                            BehaviorGroup behaviorGroup = new BehaviorGroup();
                            behaviorGroup.setBundleId(patchApp.getBundleId());
                            behaviorGroup.setDisplayName("Patch email subscription");

                            behaviorGroupRepository.createFull(
                                accountId,
                                orgId,
                                behaviorGroup,
                                List.of(endpoint.getId()),
                                Set.of(patchNewAdvisoryEventType.get().getId())
                            );
                            totalInsertedIntegrationElements++;
                        }
                        alreadyProcessedOrgIds.add(orgId);
                    }
                }
            }
        } catch (final Exception e) {
            Log.error("Unable to migrate patch subscriptions due to an exception. The insertions were rolled back", e);
            throw new InternalServerErrorException("Unable to persist subscriptions. The operation was rolled back");
        }

        Log.infof("A total of %d Patch subscriptions were persisted in the database", totalInsertedElements);
        Log.infof("A total of %d Integrations were persisted in the database", totalInsertedIntegrationElements);
    }

    protected Endpoint getOrCreateSystemSubscriptionEndpoint(String orgId, String accountId) {
        SystemSubscriptionProperties properties = new SystemSubscriptionProperties();
        properties.setOnlyAdmins(false);

        Optional<Endpoint> getEndpoint = endpointRepository.getSystemSubscriptionEndpoint(orgId, properties, EndpointType.EMAIL_SUBSCRIPTION);
        return getEndpoint.orElseGet(() -> endpointRepository.createSystemSubscriptionEndpoint(accountId, orgId, properties, EndpointType.EMAIL_SUBSCRIPTION));
    }
}
