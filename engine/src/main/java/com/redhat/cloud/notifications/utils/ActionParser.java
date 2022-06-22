package com.redhat.cloud.notifications.utils;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Parser;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ActionParser {

    public Action fromJsonString(String actionJson) {
        try {
            return Parser.decode(actionJson);
        } catch (Exception e) {
            throw new RuntimeException("Action parsing failed for payload: " + actionJson, e);
        }
    }
}
