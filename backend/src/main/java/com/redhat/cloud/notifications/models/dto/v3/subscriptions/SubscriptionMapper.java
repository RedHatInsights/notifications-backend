package com.redhat.cloud.notifications.models.dto.v3.subscriptions;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.CDI)
public interface SubscriptionMapper {

    SeverityDTO v2ToV3Severity(com.redhat.cloud.notifications.models.dto.v2.subscriptions.SeverityDTO v2);

    com.redhat.cloud.notifications.models.dto.v2.subscriptions.SeverityDTO v3ToV2Severity(SeverityDTO v3);

    SubscriptionTypeDTO v2ToV3SubscriptionType(com.redhat.cloud.notifications.models.dto.v2.subscriptions.SubscriptionTypeDTO v2);

    com.redhat.cloud.notifications.models.dto.v2.subscriptions.SubscriptionTypeDTO v3ToV2SubscriptionType(SubscriptionTypeDTO v3);

    SubscriptionChannelDTO v2ToV3Channel(com.redhat.cloud.notifications.models.dto.v2.subscriptions.SubscriptionChannelDTO v2);

    com.redhat.cloud.notifications.models.dto.v2.subscriptions.SubscriptionChannelDTO v3ToV2Channel(SubscriptionChannelDTO v3);

    EventTypeSubscriptionDTO v2ToV3EventType(com.redhat.cloud.notifications.models.dto.v2.subscriptions.EventTypeSubscriptionDTO v2);

    ApplicationSubscriptionDTO v2ToV3Application(com.redhat.cloud.notifications.models.dto.v2.subscriptions.ApplicationSubscriptionDTO v2);

    BundleSubscriptionDTO v2ToV3Bundle(com.redhat.cloud.notifications.models.dto.v2.subscriptions.BundleSubscriptionDTO v2);

    List<BundleSubscriptionDTO> v2ToV3Bundles(List<com.redhat.cloud.notifications.models.dto.v2.subscriptions.BundleSubscriptionDTO> v2);

    com.redhat.cloud.notifications.models.dto.v2.subscriptions.EventTypeSubscriptionUpdateDTO v3ToV2EventTypeUpdate(EventTypeSubscriptionUpdateDTO v3);

    com.redhat.cloud.notifications.models.dto.v2.subscriptions.ApplicationSubscriptionUpdateDTO v3ToV2ApplicationUpdate(ApplicationSubscriptionUpdateDTO v3);

    com.redhat.cloud.notifications.models.dto.v2.subscriptions.BundleSubscriptionUpdateDTO v3ToV2BundleUpdate(BundleSubscriptionUpdateDTO v3);

    List<com.redhat.cloud.notifications.models.dto.v2.subscriptions.BundleSubscriptionUpdateDTO> v3ToV2BundleUpdates(List<BundleSubscriptionUpdateDTO> v3);
}
