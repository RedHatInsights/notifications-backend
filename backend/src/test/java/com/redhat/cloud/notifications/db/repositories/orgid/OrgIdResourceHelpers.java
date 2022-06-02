package com.redhat.cloud.notifications.db.repositories.orgid;

import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.db.repositories.ApplicationRepository;
import com.redhat.cloud.notifications.db.repositories.BundleRepository;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointProperties;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.EventType;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import java.util.List;
import java.util.UUID;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static java.lang.Boolean.FALSE;

@ApplicationScoped
@Transactional
class OrgIdResourceHelpers {

    @Inject
    EndpointRepository endpointRepository;

    @Inject
    ApplicationRepository applicationRepository;

    @Inject
    BundleRepository bundleRepository;

    @Inject
    BehaviorGroupRepositoryOrgId behaviorGroupRepositoryOrgId;

    Bundle createBundle() {
        return createBundle("name", "displayName");
    }

    Bundle createBundle(String name, String displayName) {
        Bundle bundle = new Bundle(name, displayName);
        return bundleRepository.createBundle(bundle);
    }

    Application createApplication(UUID bundleId) {
        return createApplication(bundleId, "name", "displayName");
    }

    Application createApplication(UUID bundleId, String name, String displayName) {
        Application app = new Application();
        app.setBundleId(bundleId);
        app.setName(name);
        app.setDisplayName(displayName);
        return applicationRepository.createApp(app);
    }

    Endpoint createEndpoint(String orgId, EndpointType type) {
        return createEndpoint(orgId, type, null);
    }

    private Endpoint createEndpoint(String orgId, EndpointType type, String subType) {
        return createEndpoint(orgId, type, subType, "name", "description", null, FALSE);
    }

    Endpoint createEndpoint(String orgId, EndpointType type, String subType, String name, String description, EndpointProperties properties, Boolean enabled) {
        Endpoint endpoint = new Endpoint();
        endpoint.setOrgId(orgId);
        endpoint.setType(type);
        endpoint.setSubType(subType);
        endpoint.setName(name);
        endpoint.setDescription(description);
        endpoint.setProperties(properties);
        endpoint.setEnabled(enabled);
        return endpointRepository.createEndpoint(endpoint);
    }

    BehaviorGroup createDefaultBehaviorGroup(UUID bundleId) {
        BehaviorGroup behaviorGroup = new BehaviorGroup();
        behaviorGroup.setDisplayName("displayName");
        behaviorGroup.setBundleId(bundleId);
        return behaviorGroupRepositoryOrgId.createDefault(behaviorGroup);
    }

    List<EventType> findEventTypesByBehaviorGroup(UUID behaviorGroupId) {
        return behaviorGroupRepositoryOrgId.findEventTypesByBehaviorGroupId(DEFAULT_ORG_ID, behaviorGroupId);
    }

    List<BehaviorGroup> findBehaviorGroupsByEventTypeId(UUID eventTypeId) {
        return behaviorGroupRepositoryOrgId.findBehaviorGroupsByEventTypeId(DEFAULT_ORG_ID, eventTypeId, new Query());
    }

    List<BehaviorGroup> findBehaviorGroupsByEndpointId(UUID endpointId) {
        return behaviorGroupRepositoryOrgId.findBehaviorGroupsByEndpoint(DEFAULT_ORG_ID, endpointId);
    }

    Boolean updateBehaviorGroup(BehaviorGroup behaviorGroup) {
        return behaviorGroupRepositoryOrgId.update(DEFAULT_ORG_ID, behaviorGroup);
    }

    Boolean deleteBehaviorGroup(UUID behaviorGroupId) {
        return behaviorGroupRepositoryOrgId.delete(DEFAULT_ORG_ID, behaviorGroupId);
    }

    Boolean updateDefaultBehaviorGroup(BehaviorGroup behaviorGroup) {
        return behaviorGroupRepositoryOrgId.updateDefault(behaviorGroup);
    }

    Boolean deleteDefaultBehaviorGroup(UUID behaviorGroupId) {
        return behaviorGroupRepositoryOrgId.deleteDefault(behaviorGroupId);
    }

    BehaviorGroup createBehaviorGroup(String displayName, UUID bundleId) {
        BehaviorGroup behaviorGroup = new BehaviorGroup();
        behaviorGroup.setDisplayName(displayName);
        behaviorGroup.setBundleId(bundleId);
        return behaviorGroupRepositoryOrgId.create(com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID, behaviorGroup);
    }
}
