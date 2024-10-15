package com.redhat.cloud.notifications.models.dto.v1;

import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.NotificationHistory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.CDI)
public interface CommonMapper {

    @Mapping(target = "applications", ignore = true)
    BundleDTO bundleToBundleDTO(Bundle bundle);

    @Mapping(target = "bundle", ignore = true)
    ApplicationDTO applicationToApplicationDTO(Application application);

    @Mapping(target = "application", ignore = true)
    EventTypeDTO eventTypeToEventTypeDTO(EventType eventType);

    List<EventTypeDTO> eventTypeListToEventTypeDTOList(List<EventType> eventType);

    @Mapping(source = "isInvocationResult", target = "invocationResult")
    List<NotificationHistoryDTO> notificationHistoryListToNotificationHistoryDTOList(List<NotificationHistory> notificationHistory);
}
