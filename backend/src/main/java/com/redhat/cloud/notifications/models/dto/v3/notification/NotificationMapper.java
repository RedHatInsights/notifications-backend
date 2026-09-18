package com.redhat.cloud.notifications.models.dto.v3.notification;

import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EventType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.CDI)
public interface NotificationMapper {

    @Mapping(target = "applications", ignore = true)
    BundleDTO bundleToDTO(Bundle bundle);

    ApplicationDTO applicationToDTO(Application application);

    EventTypeDTO eventTypeToDTO(EventType eventType);
}
