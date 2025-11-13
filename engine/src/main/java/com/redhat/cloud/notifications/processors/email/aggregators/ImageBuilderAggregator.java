package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.models.EmailAggregation;

/**
 * ImageBuilderAggregator for image-builder application.
 * Launch event types (launch-success, launch-failed) have been removed following
 * the decommissioning of the Launch/Provisioning service.
 */
public class ImageBuilderAggregator extends AbstractEmailPayloadAggregator {

    @Override
    void processEmailAggregation(EmailAggregation notification) {
        // No event types currently processed for image-builder
        // This aggregator is kept for potential future image-builder event types
    }
}
