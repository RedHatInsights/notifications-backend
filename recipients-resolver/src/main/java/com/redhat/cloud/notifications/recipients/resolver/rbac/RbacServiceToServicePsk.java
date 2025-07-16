package com.redhat.cloud.notifications.recipients.resolver.rbac;

import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "rbac-s2s")
@RegisterProvider(AuthRequestFilter.class)
public interface RbacServiceToServicePsk extends RbacServiceToService {
}
