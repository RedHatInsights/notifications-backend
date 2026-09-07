package com.redhat.cloud.notifications.routers.handlers.endpoint;

import com.redhat.cloud.notifications.auth.ConsoleIdentityProvider;
import com.redhat.cloud.notifications.auth.kessel.KesselInventoryAuthorization;
import com.redhat.cloud.notifications.auth.principal.rhid.RhIdPrincipal;
import com.redhat.cloud.notifications.auth.rbac.RbacGroupValidator;
import com.redhat.cloud.notifications.auth.rbac.workspace.WorkspaceUtils;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.db.repositories.BehaviorGroupRepository;
import com.redhat.cloud.notifications.db.repositories.EndpointEventTypeRepository;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.db.repositories.EventTypeRepository;
import com.redhat.cloud.notifications.db.repositories.NotificationRepository;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.CompositeEndpointType;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointProperties;
import com.redhat.cloud.notifications.models.EndpointStatus;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.SourcesSecretable;
import com.redhat.cloud.notifications.models.SystemSubscriptionProperties;
import com.redhat.cloud.notifications.models.WebhookProperties;
import com.redhat.cloud.notifications.models.dto.CommonMapper;
import com.redhat.cloud.notifications.models.dto.v1.ApplicationDTO;
import com.redhat.cloud.notifications.models.dto.v1.BundleDTO;
import com.redhat.cloud.notifications.models.dto.v1.EventTypeDTO;
import com.redhat.cloud.notifications.models.dto.v1.endpoint.EndpointDTO;
import com.redhat.cloud.notifications.models.dto.v1.endpoint.EndpointMapper;
import com.redhat.cloud.notifications.routers.endpoints.EndpointTestRequest;
import com.redhat.cloud.notifications.routers.endpoints.InternalEndpointTestRequest;
import com.redhat.cloud.notifications.routers.engine.EndpointTestService;
import com.redhat.cloud.notifications.routers.models.EndpointPage;
import com.redhat.cloud.notifications.routers.models.Meta;
import com.redhat.cloud.notifications.routers.models.RequestSystemSubscriptionProperties;
import com.redhat.cloud.notifications.routers.sources.SecretUtils;
import com.redhat.cloud.notifications.security.SecurityLog;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.auth.kessel.permission.WorkspacePermission.INTEGRATIONS_EDIT;
import static com.redhat.cloud.notifications.models.Endpoint.SERVICE_NOW_ENDPOINT_SUBTYPE;
import static com.redhat.cloud.notifications.models.Endpoint.SLACK_ENDPOINT_SUBTYPE;
import static com.redhat.cloud.notifications.models.Endpoint.SPLUNK_ENDPOINT_SUBTYPE;
import static com.redhat.cloud.notifications.models.EndpointType.ANSIBLE;
import static com.redhat.cloud.notifications.models.EndpointType.CAMEL;
import static com.redhat.cloud.notifications.models.EndpointType.DRAWER;
import static com.redhat.cloud.notifications.models.EndpointType.EMAIL_SUBSCRIPTION;
import static com.redhat.cloud.notifications.models.EndpointType.WEBHOOK;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getAccountId;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getOrgId;

public class EndpointResourceCommon {

    public static final String REDACTED_CREDENTIAL = "*****";
    public static final String DEPRECATED_SLACK_CHANNEL_ERROR = "The channel field is deprecated";
    public static final String HTTPS_ENDPOINT_SCHEME_REQUIRED = "The endpoint URL must start with \"https\"";
    public static final String UNSUPPORTED_ENDPOINT_TYPE = "Unsupported endpoint type";
    public static final String AUTO_CREATED_BEHAVIOR_GROUP_NAME_TEMPLATE = "Integration \"%s\" behavior group";
    public static final String SPLUNK_HEC_TOKEN_REQUIRED = "The Splunk HEC token is required";

    @Inject
    EndpointRepository endpointRepository;

    @Inject
    EndpointMapper endpointMapper;

    @Inject
    CommonMapper commonMapper;

    @Inject
    WorkspaceUtils workspaceUtils;

    @Inject
    KesselInventoryAuthorization kesselInventoryAuthorization;

    @Inject
    BackendConfig backendConfig;

    @Inject
    EventTypeRepository eventTypeRepository;

    @Inject
    BehaviorGroupRepository behaviorGroupRepository;

    @Inject
    RbacGroupValidator rbacGroupValidator;

    @Inject
    EndpointEventTypeRepository endpointEventTypeRepository;

    @Inject
    NotificationRepository notificationRepository;

    @Inject
    @RestClient
    EndpointTestService endpointTestService;

    /**
     * Used to create the secrets in Sources and update the endpoint's properties' IDs.
     */
    @Inject
    SecretUtils secretUtils;

    protected Endpoint getEndpoint(final SecurityContext securityContext, final UUID id, final boolean includeLinkedEventTypes, final boolean includeSystemIntegrationFlag) {
        return getEndpoint(securityContext, id, includeLinkedEventTypes, includeSystemIntegrationFlag, true);
    }

    /**
     * @param loadAndRedactSecrets versions of the API whose payloads never carry secrets (e.g. v3, see
     *                             RHCLOUD-34316) don't need them loaded from Sources or redacted here, since they
     *                             get dropped when mapping to their DTO regardless.
     */
    protected Endpoint getEndpoint(final SecurityContext securityContext, final UUID id, final boolean includeLinkedEventTypes, final boolean includeSystemIntegrationFlag, final boolean loadAndRedactSecrets) {
        String orgId = getOrgId(securityContext);
        Optional<Endpoint> endpoint = endpointRepository.getEndpointWithLinkedEventTypes(orgId, id, includeSystemIntegrationFlag);
        if (endpoint.isEmpty()) {
            throw new NotFoundException();
        } else {
            if (loadAndRedactSecrets) {
                // Fetch the secrets from Sources.
                this.secretUtils.loadSecretsForEndpoint(endpoint.get());

                // Redact all the credentials from the endpoint's properties.
                redactSecretsForEndpoint(securityContext, endpoint.get());
            }

            return endpoint.get();
        }
    }

    protected EndpointDTO internalGetEndpoint(final SecurityContext securityContext, final UUID id, final boolean includeLinkedEventTypes, final boolean includeSystemIntegrationFlag) {
        Endpoint endpoint = getEndpoint(securityContext, id, includeLinkedEventTypes, includeSystemIntegrationFlag);

        EndpointDTO endpointDTO = this.endpointMapper.toDTO(endpoint);
        if (includeLinkedEventTypes) {
            endpointDTO.setEventTypesGroupByBundlesAndApplications(includeLinkedEventTypes(endpoint.getEventTypes()));
        }
        if (includeSystemIntegrationFlag) {
            endpointDTO.setReadOnly(endpoint.getOrgId() == null);
        }
        return endpointDTO;
    }

    protected record EndpointPageRecord(
            List<Endpoint> endpoints,
            Long count
    ) {
    }

    protected EndpointPageRecord getEndpoints(
            final SecurityContext sec,
            final Query query,
            final List<String> targetType,
            final Boolean activeOnly,
            final String name,
            final boolean includeLinkedEventTypes,
            final boolean includeSystemIntegrations
    ) {
        String orgId = getOrgId(sec);

        List<Endpoint> endpoints;
        Long count;

        Set<CompositeEndpointType> compositeType;

        if (targetType != null && targetType.size() > 0) {
            compositeType = targetType.stream().map(s -> {
                try {
                    return CompositeEndpointType.fromString(s);
                } catch (IllegalArgumentException e) {
                    throw new BadRequestException("Unknown endpoint type: [" + s + "]", e);
                }
            }).collect(Collectors.toSet());
        } else {
            compositeType = Set.of();
        }

        endpoints = endpointRepository.getEndpointsPerCompositeType(orgId, name, compositeType, activeOnly, query, includeSystemIntegrations);
        if (includeLinkedEventTypes && !endpoints.isEmpty()) {
            endpointRepository.loadEventTypes(endpoints);
        }
        count = endpointRepository.getEndpointsCountPerCompositeType(orgId, name, compositeType, activeOnly, includeSystemIntegrations);

        return new EndpointPageRecord(endpoints, count);
    }

    /**
     * Gets the list of endpoints.
     * @param sec the security context of the request.
     * @param query the page related query elements.
     * @param targetType the types of the endpoints to fetch.
     * @param activeOnly should only the active endpoints be fetched?
     * @param name filter endpoints by name.
     * @return a page containing the requested endpoints.
     */
    protected EndpointPage internalGetEndpoints(
        final SecurityContext sec,
        final Query query,
        final List<String> targetType,
        final Boolean activeOnly,
        final String name,
        final boolean includeLinkedEventTypes,
        final boolean includeSystemIntegrations
    ) {
        EndpointPageRecord foundEndpoints = getEndpoints(sec, query, targetType, activeOnly, name, includeLinkedEventTypes, includeSystemIntegrations);

        final List<EndpointDTO> endpointDTOS = new ArrayList<>(foundEndpoints.endpoints().size());
        for (Endpoint endpoint: foundEndpoints.endpoints()) {
            // Fetch the secrets from Sources.
            secretUtils.loadSecretsForEndpoint(endpoint);

            // Redact the secrets for the endpoint if the user does not have
            // permission.
            redactSecretsForEndpoint(sec, endpoint);

            EndpointDTO endpointDTO = endpointMapper.toDTO(endpoint);
            if (includeLinkedEventTypes) {
                endpointDTO.setEventTypesGroupByBundlesAndApplications(includeLinkedEventTypes(endpoint.getEventTypes()));
            }
            if (includeSystemIntegrations) {
                endpointDTO.setReadOnly(endpoint.getOrgId() == null);
            }
            endpointDTOS.add(endpointDTO);
        }

        return new EndpointPage(endpointDTOS, new HashMap<>(), new Meta(foundEndpoints.count()));
    }



    /**
     * Removes the secrets from the endpoint's properties when returning them
     * to the client.
     * @param endpoint the endpoint to redact the secrets from.
     */
    @Deprecated(forRemoval = true)
    protected void redactSecretsForEndpoint(final SecurityContext securityContext, final Endpoint endpoint) {
        // Figure out if the principal has "write" permissions on the
        // integration or not, to decide whether we should redact the secrets
        // from the returning payload.
        //
        // Users with just read permissions will get the secrets redacted for
        // them.
        boolean shouldRedactSecrets;
        if (this.backendConfig.isKesselEnabled(getOrgId(securityContext))) {
            final UUID workspaceId = this.workspaceUtils.getDefaultWorkspaceId(getOrgId(securityContext));
            try {
                this.kesselInventoryAuthorization.hasPermissionOnWorkspace(securityContext, INTEGRATIONS_EDIT, workspaceId);
                shouldRedactSecrets = false;
            } catch (final ForbiddenException | NotFoundException e) {
                shouldRedactSecrets = true;
            }
        } else {
            shouldRedactSecrets = !securityContext.isUserInRole(ConsoleIdentityProvider.RBAC_WRITE_INTEGRATIONS_ENDPOINTS);
        }

        if (shouldRedactSecrets) {
            if (endpoint.getProperties() instanceof SourcesSecretable sourcesSecretable) {
                final String bearerToken = sourcesSecretable.getBearerAuthentication();
                if (bearerToken != null) {
                    sourcesSecretable.setBearerAuthentication(REDACTED_CREDENTIAL);
                }

                final String secretToken = sourcesSecretable.getSecretToken();
                if (secretToken != null) {
                    sourcesSecretable.setSecretToken(REDACTED_CREDENTIAL);
                }
            }
        }
    }


    protected Set<BundleDTO> includeLinkedEventTypes(Set<EventType> eventTypes) {
        Set<BundleDTO> bundleDTOSet = null;
        if (null != eventTypes && !eventTypes.isEmpty()) {
            Map<Application, List<EventType>> applicationMap = eventTypes.stream()
                .sorted(Comparator.comparing(EventType::getDisplayName))
                .collect(Collectors.groupingBy(EventType::getApplication));
            Map<Bundle, List<Application>> bundleMap = applicationMap.keySet().stream()
                .sorted(Comparator.comparing(Application::getDisplayName))
                .collect(Collectors.groupingBy(Application::getBundle));

            List<Bundle> bundleList = bundleMap.keySet().stream().sorted(Comparator.comparing(Bundle::getDisplayName)).toList();

            bundleDTOSet = new LinkedHashSet<>();
            for (Bundle bundle : bundleList) {
                Set<ApplicationDTO> applicationDTOSet = new LinkedHashSet<>();
                List<Application> applications = bundleMap.get(bundle);
                for (Application application : applications) {
                    ApplicationDTO applicationDTO = commonMapper.applicationToApplicationDTO(application);
                    Set<EventTypeDTO> eventTypesDTO = new LinkedHashSet<>();
                    eventTypesDTO.addAll(commonMapper.eventTypeListToEventTypeDTOList(applicationMap.get(application)));
                    applicationDTO.setEventTypes(eventTypesDTO);
                    applicationDTOSet.add(applicationDTO);
                }
                BundleDTO bundleDTO = commonMapper.bundleToBundleDTO(bundle);
                bundleDTO.setApplications(applicationDTOSet);
                bundleDTOSet.add(bundleDTO);
            }
        }
        return bundleDTOSet;
    }

    /**
     * Internal function which creates the given endpoint. The reason why there
     * is this internal function is so that we can wrap it with a "try/catch"
     * block, so that if any Sources secrets are created and an exception is
     * raised upon saving the endpoint, we can call Sources again to clean up
     * the secrets, as otherwise we would be leaving dangling secrets in
     * Sources.
     *
     * @param sec        the security context of the request.
     * @param endpoint   the endpoint to be created.
     * @param eventTypes
     * @return the created endpoint in the database.
     */
    @Transactional
    protected Endpoint internalCreateEndpoint(
            final SecurityContext sec,
            final Endpoint endpoint,
            final Set<UUID> eventTypes
    ) {
        if (!isEndpointTypeAllowed(endpoint.getType())) {
            throw new BadRequestException(UNSUPPORTED_ENDPOINT_TYPE);
        }
        String accountId = getAccountId(sec);
        String orgId = getOrgId(sec);

        endpoint.setAccountId(accountId);
        endpoint.setOrgId(orgId);

        if (endpoint.getProperties() == null) {
            throw new BadRequestException("Properties is required");
        }

        if (endpoint.getType() == CAMEL) {
            if (!endpoint.isCamelSubTypeSupported()) {
                throw new BadRequestException("The sub type '" + endpoint.getSubType() + "' is not supported with type 'CAMEL'");
            }
            checkSslDisabledEndpoint(endpoint);
            String subType = endpoint.getSubType();

            if (subType.equals(SLACK_ENDPOINT_SUBTYPE)) {
                checkSlackChannel(endpoint.getProperties(CamelProperties.class), null);
            } else if (subType.equals(SERVICE_NOW_ENDPOINT_SUBTYPE) || subType.equals(SPLUNK_ENDPOINT_SUBTYPE)) {
                checkHttpsEndpoint(endpoint.getProperties(CamelProperties.class));
            }
            if (subType.equals(SPLUNK_ENDPOINT_SUBTYPE)) {
                checkSplunkHecToken(endpoint.getProperties(CamelProperties.class));
            }
        } else if (endpoint.getType() == WEBHOOK || endpoint.getType() == ANSIBLE) {
            checkSslDisabledEndpoint(endpoint);
        } else if (Set.of(EMAIL_SUBSCRIPTION, DRAWER).contains(endpoint.getType())) {
            RhIdPrincipal principal = (RhIdPrincipal) sec.getUserPrincipal();
            getOrCreateInternalEndpointCommonChecks(endpoint.getProperties(SystemSubscriptionProperties.class), principal);
        }

        endpoint.setStatus(EndpointStatus.READY);

        endpoint.setEventTypes(endpointEventTypeRepository.fetchAndValidateEndpointsEventTypesAssociation(eventTypes, Set.of(endpoint.getType())));

        this.secretUtils.createSecretsForEndpoint(endpoint);

        final Endpoint createdEndpoint = this.endpointRepository.createEndpoint(endpoint);

        // Sync behavior group model
        if (null != eventTypes && !eventTypes.isEmpty()) {
            createOrUpdateLinkedBehaviorGroup(eventTypes, createdEndpoint.getId(), createdEndpoint.getName(), orgId, accountId);
        }

        SecurityLog.logCrudSuccess("CREATE", "integration", createdEndpoint.getId().toString(), sec, "Created integration: " + createdEndpoint.getName());
        return createdEndpoint;
    }

    protected void checkSlackChannel(CamelProperties camelProperties, CamelProperties previousCamelProperties) {
        String channel = camelProperties.getExtras() != null ? camelProperties.getExtras().get("channel") : null;

        // throw an exception if we receive a channel on endpoint creation
        if (null == previousCamelProperties && channel != null) {
            throw new BadRequestException(DEPRECATED_SLACK_CHANNEL_ERROR);
            // throw an exception if we receive a channel update
        } else if (channel != null && (previousCamelProperties.getExtras() == null || !channel.equals(previousCamelProperties.getExtras().get("channel")))) {
            throw new BadRequestException(DEPRECATED_SLACK_CHANNEL_ERROR);
        }
    }

    protected void checkHttpsEndpoint(CamelProperties camelProperties) {
        if (camelProperties != null) {
            final String url = camelProperties.getUrl();
            final URI endpointUri;
            try {
                endpointUri = URI.create(url);
            } catch (final IllegalArgumentException e) {
                throw new BadRequestException("Invalid endpoint URL: " + e.getMessage());
            }

            if (endpointUri.getScheme() == null || !endpointUri.getScheme().equalsIgnoreCase("https")) {
                throw new BadRequestException(HTTPS_ENDPOINT_SCHEME_REQUIRED);
            }
        }
    }

    protected void checkSplunkHecToken(CamelProperties camelProperties) {
        if (camelProperties != null) {
            String secretToken = camelProperties.getSecretToken();
            if (secretToken == null || secretToken.isBlank()) {
                throw new BadRequestException(SPLUNK_HEC_TOKEN_REQUIRED);
            }
        }
    }

    protected void createOrUpdateLinkedBehaviorGroup(Set<UUID> eventTypeIds, UUID endpointId, String endpointName, String orgId, String accountId) {
        String behaviorGroupName = String.format(AUTO_CREATED_BEHAVIOR_GROUP_NAME_TEMPLATE, endpointName);

        // group event types by bundle
        Map<UUID, Bundle> bundlesByEventTypeId = eventTypeRepository.findBundlesByEventTypeIds(eventTypeIds);
        Map<UUID, Set<UUID>> eventTypesGroupedByBundle = new HashMap<>();
        for (UUID eventTypeId : eventTypeIds) {
            Bundle bundle = bundlesByEventTypeId.get(eventTypeId);
            if (bundle != null) {
                eventTypesGroupedByBundle.computeIfAbsent(bundle.getId(), ignored -> new HashSet<>())
                        .add(eventTypeId);
            }
        }

        for (UUID bundleId : eventTypesGroupedByBundle.keySet()) {
            Optional<BehaviorGroup> existingBg = behaviorGroupRepository.findBehaviorGroupsByName(orgId, bundleId, behaviorGroupName);
            if (existingBg.isPresent()) {
                Boolean alreadyAssociatedAction = existingBg.get().getActions().stream().anyMatch(bga -> bga.getId().endpointId.equals(endpointId));

                if (!alreadyAssociatedAction) {
                    int position = existingBg.get().getActions().stream().mapToInt(ba -> ba.getPosition()).max().orElse(-1) + 1;
                    behaviorGroupRepository.appendActionToBehaviorGroup(existingBg.get().getId(), endpointId, position, orgId);
                }
                for (UUID eventTypeId : eventTypesGroupedByBundle.get(bundleId)) {
                    Boolean alreadyAssociatedEventType = existingBg.get().getBehaviors().stream().anyMatch(bh -> bh.getId().eventTypeId.equals(eventTypeId));
                    if (!alreadyAssociatedEventType) {
                        behaviorGroupRepository.appendBehaviorGroupToEventType(orgId, existingBg.get().getId(), eventTypeId);
                    }
                }
            } else {
                // Create or update legacy behavior group structure
                BehaviorGroup behaviorGroup = new BehaviorGroup();
                behaviorGroup.setBundleId(bundleId);
                behaviorGroup.setDisplayName(behaviorGroupName);

                behaviorGroupRepository.createFull(
                        accountId,
                        orgId,
                        behaviorGroup,
                        List.of(endpointId),
                        eventTypesGroupedByBundle.get(bundleId)
                );
            }
        }
    }

    protected boolean isEndpointTypeAllowed(EndpointType endpointType) {
        return !backendConfig.isEmailsOnlyModeEnabled() || EMAIL_SUBSCRIPTION.equals(endpointType) || DRAWER.equals(endpointType);
    }

    /** @deprecated to be removed once all endpoints with {@code disableSslVerification = true} are deleted. */
    @Deprecated(forRemoval = true)
    protected void checkSslDisabledEndpoint(String orgId, UUID id) {
        checkSslDisabledEndpoint(endpointRepository.getEndpoint(orgId, id));
    }

    /**
     * @param endpoint A {@link EndpointType#CAMEL}, {@link EndpointType#WEBHOOK}, or {@link EndpointType#ANSIBLE} endpoint.
     * @deprecated to be removed once all endpoints with {@code disableSslVerification = true} are deleted.
     */
    @Deprecated(forRemoval = true)
    protected void checkSslDisabledEndpoint(Endpoint endpoint) {
        // Early return to simplify error message
        if (endpoint.getType() == CAMEL) {
            if (endpoint.getProperties() == null || !endpoint.getProperties(CamelProperties.class).getDisableSslVerification()) {
                return;
            }
        } else {
            if (endpoint.getProperties() == null || !endpoint.getProperties(WebhookProperties.class).getDisableSslVerification()) {
                return;
            }
        }

        throw new BadRequestException("Endpoints are no longer permitted to disable SSL/TLS verification, and existing integrations which have disabled " +
                "verification will be removed soon. Please enable SSL/TLS verification to continue using this integration, or contact Red Hat Support for assistance.");
    }

    @Deprecated(forRemoval = true)
    protected void getOrCreateInternalEndpointCommonChecks(SystemSubscriptionProperties requestProps, RhIdPrincipal principal) {
        if (requestProps.getGroupId() != null && requestProps.isOnlyAdmins()) {
            throw new BadRequestException("Cannot use RBAC groups and only admins in the same endpoint");
        }

        if (requestProps.getGroupId() != null) {
            boolean isValid = rbacGroupValidator.validate(requestProps.getGroupId(), principal.getIdentity().rawIdentity);
            if (!isValid) {
                throw new BadRequestException(String.format("Invalid RBAC group identified with id %s", requestProps.getGroupId()));
            }
        }
    }

    @Deprecated(forRemoval = true)
    protected void getOrCreateInternalEndpointCommonChecks(RequestSystemSubscriptionProperties requestProps, RhIdPrincipal principal) {
        if (requestProps.getGroupId() != null && requestProps.isOnlyAdmins()) {
            throw new BadRequestException("Cannot use RBAC groups and only admins in the same endpoint");
        }

        if (requestProps.getGroupId() != null) {
            boolean isValid = rbacGroupValidator.validate(requestProps.getGroupId(), principal.getIdentity().rawIdentity);
            if (!isValid) {
                throw new BadRequestException(String.format("Invalid RBAC group identified with id %s", requestProps.getGroupId()));
            }
        }
    }

    protected Response deleteEndpoint(SecurityContext sec, UUID id) {
        String orgId = getOrgId(sec);
        EndpointType endpointType = endpointRepository.getEndpointTypeById(orgId, id);
        if (!isEndpointTypeAllowed(endpointType)) {
            throw new BadRequestException(UNSUPPORTED_ENDPOINT_TYPE);
        }

        // Clean up the secrets in Sources.
        final Endpoint endpoint = endpointRepository.getEndpoint(orgId, id);

        endpointRepository.deleteEndpoint(orgId, id);

        // Attempt deleting the secrets for the given endpoint. In the case
        // that the secrets deletion goes wrong:
        //
        // - The transaction will be rolled back and the integration will not
        // be deleted.
        // - The secrets will not have been deleted from Sources.
        // - We need to recreate the integration in Kessel Inventory, so that
        // everything stays in sync.
        try {
            this.secretUtils.deleteSecretsForEndpoint(endpoint);
        } catch (final Exception e) {
            if (this.backendConfig.isIgnoreSourcesErrorOnEndpointDelete(orgId)) {
                Log.errorf(e, "Sources error deleting endpoint %s", endpoint);
            } else {
                throw e;
            }
        }

        SecurityLog.logCrudSuccess("DELETE", "integration", id.toString(), sec, "Deleted integration");

        return Response.noContent().build();
    }

    protected Response enableEndpoint(SecurityContext sec, UUID id) {
        String orgId = getOrgId(sec);
        EndpointType endpointType = endpointRepository.getEndpointTypeById(orgId, id);
        if (!isEndpointTypeAllowed(endpointType)) {
            throw new BadRequestException(UNSUPPORTED_ENDPOINT_TYPE);
        }

        if (List.of(CAMEL, WEBHOOK, ANSIBLE).contains(endpointType)) {
            checkSslDisabledEndpoint(orgId, id);
        }
        endpointRepository.enableEndpoint(orgId, id);

        SecurityLog.logCrudSuccess("UPDATE", "integration", id.toString(), sec, "Enabled integration");
        return Response.ok().build();
    }

    protected Response disableEndpoint(SecurityContext sec, UUID id) {
        String orgId = getOrgId(sec);
        EndpointType endpointType = endpointRepository.getEndpointTypeById(orgId, id);
        if (!isEndpointTypeAllowed(endpointType)) {
            throw new BadRequestException(UNSUPPORTED_ENDPOINT_TYPE);
        }
        endpointRepository.disableEndpoint(orgId, id);
        SecurityLog.logCrudSuccess("UPDATE", "integration", id.toString(), sec, "Disabled integration");

        return Response.noContent().build();
    }

    protected Response getDetailedEndpointHistory(SecurityContext sec, UUID endpointId, UUID historyId) {
        String orgId = getOrgId(sec);
        JsonObject json = notificationRepository.getNotificationDetails(orgId, endpointId, historyId);
        if (json == null) {
            // Maybe 404 should only be returned if history_id matches nothing? Otherwise 204
            throw new NotFoundException();
        } else {
            if (json.isEmpty()) {
                return Response.noContent().build();
            }
            return Response.ok(json).build();
        }
    }

    /**
     * Creates, updates or deletes (when both arguments are {@code null}) an endpoint's secrets, without ever
     * returning their contents. See RHCLOUD-34316.
     */
    @Transactional
    protected Response updateEndpointSecrets(final SecurityContext sec, final UUID id, final String secretToken, final String bearerAuthentication) {
        final String orgId = getOrgId(sec);

        final Endpoint endpoint = endpointRepository.getEndpoint(orgId, id);
        if (endpoint == null) {
            throw new NotFoundException("Endpoint not found");
        }

        if (!(endpoint.getProperties() instanceof SourcesSecretable props)) {
            throw new BadRequestException("This endpoint type does not support secrets");
        }

        props.setSecretToken(secretToken);
        props.setBearerAuthentication(bearerAuthentication);
        this.secretUtils.updateSecretsForEndpoint(endpoint);

        SecurityLog.logCrudSuccess("UPDATE", "integration-secrets", id.toString(), sec, "Updated secrets for integration");
        return Response.noContent().build();
    }

    protected Response deleteEndpointSecrets(final SecurityContext sec, final UUID id) {
        Response response = updateEndpointSecrets(sec, id, null, null);
        SecurityLog.logCrudSuccess("DELETE", "integration-secrets", id.toString(), sec, "Deleted secrets for integration");
        return response;
    }

    protected void commonTestEndpoint(SecurityContext sec, UUID uuid, final EndpointTestRequest requestBody) {
        if (!this.endpointRepository.existsByUuidAndOrgId(uuid, getOrgId(sec))) {
            throw new NotFoundException("integration not found");
        }
        EndpointType endpointType = endpointRepository.getEndpointTypeById(getOrgId(sec), uuid);
        if (endpointType == CAMEL || endpointType == WEBHOOK || endpointType == ANSIBLE) {
            checkSslDisabledEndpoint(getOrgId(sec), uuid);
        }

        final InternalEndpointTestRequest internalEndpointTestRequest = new InternalEndpointTestRequest();
        internalEndpointTestRequest.endpointUuid = uuid;
        internalEndpointTestRequest.orgId = getOrgId(sec);
        if (requestBody != null) {
            internalEndpointTestRequest.message = requestBody.message;
        }

        this.endpointTestService.testEndpoint(internalEndpointTestRequest);
    }

    protected Response commonUpdateEndpoint(SecurityContext securityContext, UUID id, final Endpoint endpoint, final Set<UUID> eventTypes, final boolean manageSecretsFromPayload) {

        if (!isEndpointTypeAllowed(endpoint.getType())) {
            throw new BadRequestException(UNSUPPORTED_ENDPOINT_TYPE);
        }
        RhIdPrincipal principal = (RhIdPrincipal) securityContext.getUserPrincipal();
        String accountId = getAccountId(securityContext);
        String orgId = getOrgId(securityContext);
        endpoint.setAccountId(accountId);
        endpoint.setOrgId(orgId);
        endpoint.setId(id);

        final Endpoint dbEndpoint = endpointRepository.getEndpoint(orgId, id);
        if (dbEndpoint == null) {
            throw new NotFoundException("Endpoint not found");
        }
        EndpointType endpointType = dbEndpoint.getType();

        if (endpointType != endpoint.getType() ||
                !Objects.equals(dbEndpoint.getSubType(), endpoint.getSubType())) {
            throw new BadRequestException("The integration type or sub type can't be modified");
        }

        if (endpoint.getType() == CAMEL) {
            String subType = endpoint.getSubType();
            checkSslDisabledEndpoint(endpoint);

            // If SSL verification is disabled on the existing endpoint, only permit updates which re-enable verification.
            try {
                if (endpointType == CAMEL) {
                    checkSslDisabledEndpoint(dbEndpoint);
                }
            } catch (BadRequestException e) {
                if (endpoint.getProperties(CamelProperties.class).getDisableSslVerification()) {
                    throw e;
                }
            }

            if (subType.equals(SLACK_ENDPOINT_SUBTYPE)) {
                checkSlackChannel(endpoint.getProperties(CamelProperties.class), dbEndpoint.getProperties(CamelProperties.class));
            } else if (subType.equals(SERVICE_NOW_ENDPOINT_SUBTYPE) || subType.equals(SPLUNK_ENDPOINT_SUBTYPE)) {
                checkHttpsEndpoint(endpoint.getProperties(CamelProperties.class));
            }
            if (subType.equals(SPLUNK_ENDPOINT_SUBTYPE)) {
                checkSplunkHecToken(endpoint.getProperties(CamelProperties.class));
            }
        } else if (Set.of(EMAIL_SUBSCRIPTION, DRAWER).contains(endpoint.getType())) {
            getOrCreateInternalEndpointCommonChecks(endpoint.getProperties(SystemSubscriptionProperties.class), principal);
        } else if (endpoint.getType() == WEBHOOK || endpoint.getType() == ANSIBLE) {
            checkSslDisabledEndpoint(endpoint);
            // If SSL verification is disabled on the existing endpoint, only permit updates which re-enable verification.
            try {
                if (endpointType == WEBHOOK || endpointType == ANSIBLE) {
                    checkSslDisabledEndpoint(dbEndpoint);
                }
            } catch (BadRequestException e) {
                if (endpoint.getProperties(WebhookProperties.class).getDisableSslVerification()) {
                    throw e;
                }
            }
        }

        endpointRepository.updateEndpoint(endpoint);

        if (!dbEndpoint.getName().equals(endpoint.getName())) {
            String behaviorGroupName = String.format(AUTO_CREATED_BEHAVIOR_GROUP_NAME_TEMPLATE, dbEndpoint.getName());
            String newBehaviorGroupName = String.format(AUTO_CREATED_BEHAVIOR_GROUP_NAME_TEMPLATE, endpoint.getName());
            behaviorGroupRepository.updateBehaviorGroupName(dbEndpoint.getOrgId(), behaviorGroupName, newBehaviorGroupName);
        }

        // Update the secrets in Sources. Versions of the API whose payloads no longer carry
        // secrets (e.g. v3, see RHCLOUD-34316) must not go through this block: their mapped
        // entity's secret fields are always null, which would otherwise be interpreted as "the
        // caller wants the secret deleted" on every plain update. Those versions manage secrets
        // exclusively through updateEndpointSecrets()/deleteEndpointSecrets() below.
        if (manageSecretsFromPayload) {
            final Endpoint updatedDbEndpoint = endpointRepository.getEndpoint(orgId, id);
            final EndpointProperties endpointProperties = endpoint.getProperties();
            final EndpointProperties databaseEndpointProperties = updatedDbEndpoint.getProperties();

            if (endpointProperties instanceof SourcesSecretable incomingProperties && databaseEndpointProperties instanceof SourcesSecretable dep) {
                // In order to be able to update the secrets in Sources, we need to grab the IDs of these secrets from the
                // database endpoint, since the client won't be sending those IDs.
                dep.setSecretToken(incomingProperties.getSecretToken());
                dep.setBearerAuthentication(incomingProperties.getBearerAuthentication());
                this.secretUtils.updateSecretsForEndpoint(updatedDbEndpoint);
            }
        }

        if (null != eventTypes) {
            internalUpdateEventTypesLinkedToEndpoint(securityContext, id, eventTypes);
        }
        SecurityLog.logCrudSuccess("UPDATE", "integration", id.toString(), securityContext, "Updated integration");
        return Response.ok().build();
    }

    protected void internalUpdateEventTypesLinkedToEndpoint(final SecurityContext securityContext, final UUID endpointId, final Set<UUID> eventTypeIds) {
        final String orgId = getOrgId(securityContext);
        final String accountId = getAccountId(securityContext);
        final Endpoint updatedEndpoint = endpointEventTypeRepository.updateEventTypesLinkedToEndpoint(endpointId, eventTypeIds, orgId);

        // Sync behavior group model

        // delete endpoint from existing behavior group
        List<BehaviorGroup> behaviorGroupsLinkedToThisEndpoint = behaviorGroupRepository.findBehaviorGroupsByEndpointId(orgId, endpointId);
        for (BehaviorGroup behaviorGroup : behaviorGroupsLinkedToThisEndpoint) {
            if (behaviorGroup.getActions().size() == 1) {
                behaviorGroupRepository.delete(orgId, behaviorGroup.getId());
            } else {
                behaviorGroupRepository.deleteEndpointFromBehaviorGroup(behaviorGroup.getId(), endpointId, orgId);
            }
        }

        // Create or update relevant behavior groups
        createOrUpdateLinkedBehaviorGroup(eventTypeIds, endpointId, updatedEndpoint.getName(), orgId, accountId);
    }
}
