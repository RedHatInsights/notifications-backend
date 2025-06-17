package com.redhat.cloud.notifications.routers.handlers.endpoint;

import com.redhat.cloud.notifications.auth.ConsoleIdentityProvider;
import com.redhat.cloud.notifications.auth.kessel.KesselAuthorization;
import com.redhat.cloud.notifications.auth.kessel.KesselInventoryAuthorization;
import com.redhat.cloud.notifications.auth.kessel.permission.IntegrationPermission;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.BasicAuthentication;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.CompositeEndpointType;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.SourcesSecretable;
import com.redhat.cloud.notifications.models.dto.v1.ApplicationDTO;
import com.redhat.cloud.notifications.models.dto.v1.BundleDTO;
import com.redhat.cloud.notifications.models.dto.v1.CommonMapper;
import com.redhat.cloud.notifications.models.dto.v1.EventTypeDTO;
import com.redhat.cloud.notifications.models.dto.v1.endpoint.EndpointDTO;
import com.redhat.cloud.notifications.models.dto.v1.endpoint.EndpointMapper;
import com.redhat.cloud.notifications.routers.models.EndpointPage;
import com.redhat.cloud.notifications.routers.models.Meta;
import com.redhat.cloud.notifications.routers.sources.SecretUtils;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.SecurityContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;


import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getOrgId;

public class EndpointResourceCommon {

    public static final String REDACTED_CREDENTIAL = "*****";

    @Inject
    EndpointRepository endpointRepository;

    @Inject
    EndpointMapper endpointMapper;

    @Inject
    CommonMapper commonMapper;

    @Inject
    KesselAuthorization kesselAuthorization;

    @Inject
    KesselInventoryAuthorization kesselInventoryAuthorization;

    @Inject
    BackendConfig backendConfig;

    /**
     * Used to create the secrets in Sources and update the endpoint's properties' IDs.
     */
    @Inject
    SecretUtils secretUtils;

    protected EndpointDTO internalGetEndpoint(final SecurityContext securityContext, final UUID id, final boolean includeLinkedEventTypes) {
        String orgId = getOrgId(securityContext);
        Optional<Endpoint> endpoint = endpointRepository.getEndpointWithLinkedEventTypes(orgId, id);
        if (endpoint.isEmpty()) {
            throw new NotFoundException();
        } else {
            // Fetch the secrets from Sources.
            this.secretUtils.loadSecretsForEndpoint(endpoint.get());

            // Redact all the credentials from the endpoint's properties.
            redactSecretsForEndpoint(securityContext, endpoint.get());

            EndpointDTO endpointDTO = this.endpointMapper.toDTO(endpoint.get());
            if (includeLinkedEventTypes) {
                includeLinkedEventTypes(endpoint.get().getEventTypes(), endpointDTO);
            }
            return endpointDTO;
        }
    }

    /**
     * Gets the list of endpoints.
     * @param sec the security context of the request.
     * @param query the page related query elements.
     * @param targetType the types of the endpoints to fetch.
     * @param activeOnly should only the active endpoints be fetched?
     * @param name filter endpoints by name.
     * @param authorizedIds set of authorized integrations that we are allowed
     *                      to fetch.
     * @return a page containing the requested endpoints.
     */
    protected EndpointPage internalGetEndpoints(
        final SecurityContext sec,
        final Query query,
        final List<String> targetType,
        final Boolean activeOnly,
        final String name,
        final Set<UUID> authorizedIds,
        final boolean includeLinkedEventTypes
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

        endpoints = endpointRepository.getEndpointsPerCompositeType(orgId, name, compositeType, activeOnly, query, authorizedIds);
        count = endpointRepository.getEndpointsCountPerCompositeType(orgId, name, compositeType, activeOnly, authorizedIds);

        final List<EndpointDTO> endpointDTOS = new ArrayList<>(endpoints.size());
        for (Endpoint endpoint: endpoints) {
            // Fetch the secrets from Sources.
            this.secretUtils.loadSecretsForEndpoint(endpoint);

            // Redact the secrets for the endpoint if the user does not have
            // permission.
            this.redactSecretsForEndpoint(sec, endpoint);

            EndpointDTO endpointDTO = endpointMapper.toDTO(endpoint);
            if (includeLinkedEventTypes) {
                includeLinkedEventTypes(endpoint.getEventTypes(), endpointDTO);
            }
            endpointDTOS.add(endpointDTO);
        }

        return new EndpointPage(endpointDTOS, new HashMap<>(), new Meta(count));
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
        if (this.backendConfig.isKesselRelationsEnabled(getOrgId(securityContext))) {
            try {
                if (this.backendConfig.isKesselInventoryUseForPermissionsChecksEnabled(getOrgId(securityContext))) {
                    this.kesselInventoryAuthorization.hasPermissionOnIntegration(securityContext, IntegrationPermission.EDIT, endpoint.getId());
                } else {
                    this.kesselAuthorization.hasPermissionOnIntegration(securityContext, IntegrationPermission.EDIT, endpoint.getId());
                }
                shouldRedactSecrets = false;
            } catch (final ForbiddenException | NotFoundException e) {
                shouldRedactSecrets = true;
            }
        } else {
            shouldRedactSecrets = !securityContext.isUserInRole(ConsoleIdentityProvider.RBAC_WRITE_INTEGRATIONS_ENDPOINTS);
        }

        if (shouldRedactSecrets) {
            if (endpoint.getProperties() instanceof SourcesSecretable sourcesSecretable) {
                final BasicAuthentication basicAuthentication = sourcesSecretable.getBasicAuthentication();
                if (basicAuthentication != null) {
                    basicAuthentication.setPassword(REDACTED_CREDENTIAL);
                    basicAuthentication.setUsername(REDACTED_CREDENTIAL);
                }

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

    protected void includeLinkedEventTypes(Set<EventType> eventTypes, EndpointDTO endpointDTO) {
        if (null != eventTypes && !eventTypes.isEmpty()) {
            Map<Application, List<EventType>> applicationMap = eventTypes.stream()
                .sorted(Comparator.comparing(EventType::getDisplayName))
                .collect(Collectors.groupingBy(EventType::getApplication));
            Map<Bundle, List<Application>> bundleMap = applicationMap.keySet().stream()
                .sorted(Comparator.comparing(Application::getDisplayName))
                .collect(Collectors.groupingBy(Application::getBundle));

            List<Bundle> bundleList = bundleMap.keySet().stream().sorted(Comparator.comparing(Bundle::getDisplayName)).toList();

            Set<BundleDTO> bundleDTOSet = new LinkedHashSet<>();
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
            endpointDTO.setEventTypesGroupByBundlesAndApplications(bundleDTOSet);
        }
    }
}
