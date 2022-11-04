package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.recipients.User;

import java.util.Map;

public class AggregatedData {

    public User user;
    public Map<
            /* application */ String,
            /* data */ Map<String, Object>
        > aggregatedDataByApplication;
}
