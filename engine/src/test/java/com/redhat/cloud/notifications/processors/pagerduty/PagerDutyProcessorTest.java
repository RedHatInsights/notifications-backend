package com.redhat.cloud.notifications.processors.pagerduty;

import com.redhat.cloud.notifications.processors.camel.CamelProcessor;
import com.redhat.cloud.notifications.processors.camel.CamelProcessorTest;
import jakarta.inject.Inject;

// TODO implement processor test for PagerDuty
public class PagerDutyProcessorTest extends CamelProcessorTest {

    @Inject
    PagerDutyProcessor pagerDutyProcessor;

    @Override
    protected String getQuteTemplate() {
        return "";
    }

    @Override
    protected String getExpectedMessage() {
        return "";
    }

    @Override
    protected String getSubType() {
        return "";
    }

    @Override
    protected CamelProcessor getCamelProcessor() {
        return null;
    }

    @Override
    protected String getExpectedConnectorHeader() {
        return "";
    }
}
