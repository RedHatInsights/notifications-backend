package com.redhat.cloud.notifications.routers.handlers.notification;

import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.db.repositories.ApplicationRepository;
import com.redhat.cloud.notifications.db.repositories.BehaviorGroupRepository;
import com.redhat.cloud.notifications.db.repositories.BundleRepository;
import com.redhat.cloud.notifications.db.repositories.EndpointEventTypeRepository;
import com.redhat.cloud.notifications.db.repositories.EventTypeRepository;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.BehaviorGroupAction;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.routers.models.Meta;
import com.redhat.cloud.notifications.routers.models.Page;
import com.redhat.cloud.notifications.routers.models.PageLinksBuilder;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getAccountId;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getOrgId;

public class NotificationResourceCommon {

    @Inject
    BundleRepository bundleRepository;

    @Inject
    ApplicationRepository applicationRepository;

    @Inject
    BehaviorGroupRepository behaviorGroupRepository;

    @Inject
    EndpointEventTypeRepository endpointEventTypeRepository;

    @Inject
    EventTypeRepository eventTypeRepository;

    @Inject
    BackendConfig backendConfig;

    protected Page<EventType> getEventTypes(
        SecurityContext securityContext, UriInfo uriInfo, @Valid Query query, Set<UUID> applicationIds,
        UUID bundleId, String eventTypeName, boolean excludeMutedTypes
    ) {
        List<UUID> unmutedEventTypeIds = excludeMutedTypes
            ? behaviorGroupRepository.findUnmutedEventTypes(getOrgId(securityContext), bundleId)
            : null;

        final String orgId = getOrgId(securityContext);
        final boolean showHiddenEventTypes = backendConfig.isShowHiddenEventTypes(orgId);
        List<EventType> eventTypes = applicationRepository.getEventTypes(query, applicationIds, bundleId, eventTypeName, excludeMutedTypes, unmutedEventTypeIds, showHiddenEventTypes);
        Long count = applicationRepository.getEventTypesCount(applicationIds, bundleId, eventTypeName, excludeMutedTypes, unmutedEventTypeIds, showHiddenEventTypes);
        return new Page<>(
            eventTypes,
            PageLinksBuilder.build(uriInfo, count, query.getLimit().getLimit(), query.getLimit().getOffset()),
            new Meta(count)
        );
    }

    protected Bundle getBundleByName(String bundleName) {
        Bundle bundle = bundleRepository.getBundle(bundleName);
        if (bundle == null) {
            throw new NotFoundException();
        }

        return bundle;
    }

    protected Application getApplicationByNameAndBundleName(String bundleName, String applicationName) {
        Application application = applicationRepository.getApplication(bundleName, applicationName);
        if (application == null) {
            throw new NotFoundException();
        }

        return application;
    }

    protected EventType getEventTypesByNameAndBundleAndApplicationName(String bundleName, String applicationName, String eventTypeName) {
        EventType eventType = applicationRepository.getEventType(bundleName, applicationName, eventTypeName);
        if (eventType == null) {
            throw new NotFoundException();
        }

        return eventType;
    }

    @Transactional
    protected Response updateEventTypeEndpoints(SecurityContext securityContext, UUID eventTypeId, Set<UUID> endpointsIds) {
        if (endpointsIds == null) {
            throw new BadRequestException("The request body must contain a list (possibly empty) of endpoints identifiers");
        }
        // RESTEasy does not reject an invalid List<UUID> body (even when @Valid is used) so we have to do an additional check here.
        if (endpointsIds.contains(null)) {
            throw new BadRequestException("The endpoints identifiers list should not contain empty values");
        }

        String orgId = getOrgId(securityContext);
        String accountId = getAccountId(securityContext);

        endpointEventTypeRepository.updateEventTypeEndpoints(orgId, eventTypeId, endpointsIds);

        // Sync behavior group model

        // delete endpoint from existing behavior group
        List<BehaviorGroup> behaviorGroupsLinkedToThisEndpoint = behaviorGroupRepository.findBehaviorGroupsByEventTypeId(orgId, eventTypeId, null);
        for (BehaviorGroup behaviorGroup : behaviorGroupsLinkedToThisEndpoint) {
            Set<UUID> associatedEventTypes = behaviorGroup.getBehaviors().stream().map(b -> b.getEventType().getId()).collect(Collectors.toSet());
            associatedEventTypes.remove(eventTypeId);
            if (associatedEventTypes.isEmpty()) {
                behaviorGroupRepository.delete(orgId, behaviorGroup.getId());
            } else {
                behaviorGroupRepository.updateBehaviorEventTypes(orgId, behaviorGroup.getId(), associatedEventTypes);
            }
        }

        createOrUpdateLinkedBehaviorGroup(eventTypeId, endpointsIds, orgId, accountId);

        return Response.ok().build();
    }

    private void createOrUpdateLinkedBehaviorGroup(UUID eventTypeId, Set<UUID> endpointIds, String orgId, String accountId) {
        if (endpointIds.isEmpty()) {
            return;
        }

        List<EventType> eventTypes = eventTypeRepository.findByIds(Set.of(eventTypeId));
        if (eventTypes.isEmpty()) {
            throw new NotFoundException("Event type not found");
        }
        EventType eventType = eventTypes.getFirst();
        String behaviorGroupName = String.format("Event type \"%s\" behavior group", eventType.getName());

        Bundle bundle = eventTypeRepository.findBundleByEventTypeId(eventTypeId)
            .orElseThrow(() -> new NotFoundException("Bundle not found for event type"));

        Optional<BehaviorGroup> existingBg = behaviorGroupRepository.findBehaviorGroupsByName(orgId, bundle.getId(), behaviorGroupName);

        if (existingBg.isEmpty()) {
            BehaviorGroup behaviorGroup = new BehaviorGroup();
            behaviorGroup.setBundleId(bundle.getId());
            behaviorGroup.setDisplayName(behaviorGroupName);

            behaviorGroupRepository.createFull(
                accountId,
                orgId,
                behaviorGroup,
                List.copyOf(endpointIds),
                Set.of(eventTypeId)
            );
        } else {
            BehaviorGroup bg = existingBg.get();

            boolean alreadyAssociatedEventType = bg.getBehaviors().stream().anyMatch(bh -> bh.getId().eventTypeId.equals(eventTypeId));
            if (!alreadyAssociatedEventType) {
                behaviorGroupRepository.appendBehaviorGroupToEventType(orgId, bg.getId(), eventTypeId);
            }

            for (UUID endpointId : endpointIds) {
                boolean alreadyAssociatedAction = bg.getActions().stream().anyMatch(bga -> bga.getId().endpointId.equals(endpointId));
                if (!alreadyAssociatedAction) {
                    int position = bg.getActions().stream().mapToInt(BehaviorGroupAction::getPosition).max().orElse(-1) + 1;
                    behaviorGroupRepository.appendActionToBehaviorGroup(bg.getId(), endpointId, position, orgId);
                }
            }
        }
    }
}
