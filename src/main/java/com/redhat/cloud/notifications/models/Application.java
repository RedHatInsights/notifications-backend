package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Date;
import java.util.Set;

public class Application {
    private Integer id;
    private Set<EventType> eventTypes;
    private String name;
    private String description;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Date created;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Date updated;

}
