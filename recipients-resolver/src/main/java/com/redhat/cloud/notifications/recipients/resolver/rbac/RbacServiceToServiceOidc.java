package com.redhat.cloud.notifications.recipients.resolver.rbac;

import io.quarkus.oidc.client.filter.OidcClientFilter;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "rbac-s2s-oidc")
@OidcClientFilter
public interface RbacServiceToServiceOidc extends RbacServiceToService {
}
