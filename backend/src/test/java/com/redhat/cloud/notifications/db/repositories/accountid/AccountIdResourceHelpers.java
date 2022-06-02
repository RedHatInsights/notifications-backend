package com.redhat.cloud.notifications.db.repositories.accountid;

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

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
import static java.lang.Boolean.FALSE;

@ApplicationScoped
@Transactional
class AccountIdResourceHelpers {

    @Inject
    EndpointRepository endpointRepository;

    @Inject
    ApplicationRepository applicationRepository;

    @Inject
    BundleRepository bundleRepository;

    @Inject
    BehaviorGroupRepository behaviorGroupRepository;

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

    Endpoint createEndpoint(String accountId, EndpointType type) {
        return createEndpoint(accountId, type, null);
    }

    Endpoint createEndpoint(String accountId, EndpointType type, String subType) {
        return createEndpoint(accountId, type, subType, "name", "description", null, FALSE);
    }

    Endpoint createEndpoint(String accountId, EndpointType type, String subType, String name, String description, EndpointProperties properties, Boolean enabled) {
        Endpoint endpoint = new Endpoint();
        endpoint.setAccountId(accountId);
        endpoint.setType(type);
        endpoint.setSubType(subType);
        endpoint.setName(name);
        endpoint.setDescription(description);
        endpoint.setProperties(properties);
        endpoint.setEnabled(enabled);
        return endpointRepository.createEndpoint(endpoint);
    }

    BehaviorGroup createBehaviorGroup(String accountId, String displayName, UUID bundleId) {
        BehaviorGroup behaviorGroup = new BehaviorGroup();
        behaviorGroup.setDisplayName(displayName);
        behaviorGroup.setBundleId(bundleId);
        return behaviorGroupRepository.create(accountId, behaviorGroup);
    }

    BehaviorGroup createDefaultBehaviorGroup(String displayName, UUID bundleId) {
        BehaviorGroup behaviorGroup = new BehaviorGroup();
        behaviorGroup.setDisplayName(displayName);
        behaviorGroup.setBundleId(bundleId);
        return behaviorGroupRepository.createDefault(behaviorGroup);
    }

    List<EventType> findEventTypesByBehaviorGroupId(UUID behaviorGroupId) {
        return behaviorGroupRepository.findEventTypesByBehaviorGroupId(DEFAULT_ACCOUNT_ID, behaviorGroupId);
    }

    List<BehaviorGroup> findBehaviorGroupsByEventTypeId(UUID eventTypeId) {
        return behaviorGroupRepository.findBehaviorGroupsByEventTypeId(DEFAULT_ACCOUNT_ID, eventTypeId, new Query());
    }

    List<BehaviorGroup> findBehaviorGroupsByEndpointId(UUID endpointId) {
        return behaviorGroupRepository.findBehaviorGroupsByEndpointId(DEFAULT_ACCOUNT_ID, endpointId);
    }

    Boolean updateBehaviorGroup(BehaviorGroup behaviorGroup) {
        return behaviorGroupRepository.update(DEFAULT_ACCOUNT_ID, behaviorGroup);
    }

    Boolean deleteBehaviorGroup(UUID behaviorGroupId) {
        return behaviorGroupRepository.delete(DEFAULT_ACCOUNT_ID, behaviorGroupId);
    }

    Boolean updateDefaultBehaviorGroup(BehaviorGroup behaviorGroup) {
        return behaviorGroupRepository.updateDefault(behaviorGroup);
    }

    Boolean deleteDefaultBehaviorGroup(UUID behaviorGroupId) {
        return behaviorGroupRepository.deleteDefault(behaviorGroupId);
    }

}
