package com.redhat.cloud.notifications.recipients.resolver.kessel;

import com.redhat.cloud.notifications.ingress.RecipientsAuthorizationCriterion;
import com.redhat.cloud.notifications.recipients.config.RecipientsResolverConfig;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.project_kessel.api.relations.v1beta1.LookupSubjectsRequest;
import org.project_kessel.api.relations.v1beta1.LookupSubjectsResponse;
import org.project_kessel.api.relations.v1beta1.ObjectReference;
import org.project_kessel.api.relations.v1beta1.ObjectType;
import org.project_kessel.relations.client.LookupClient;
import org.project_kessel.relations.client.RelationsConfig;
import org.project_kessel.relations.client.RelationsGrpcClientsManager;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class KesselService {

    static final String SUBJECT_TYPE_USER = "principal";

    static final String RBAC_NAMESPACE = "rbac";

    @Inject
    RecipientsResolverConfig recipientsResolverConfig;

    LookupClient lookupClient;

    @PostConstruct
    void postConstruct() {
        RelationsConfig kesselRelationsConfig = getKesselRelationsConfig();

        RelationsGrpcClientsManager clientsManager = RelationsGrpcClientsManager.forClientsWithConfig(kesselRelationsConfig);

        lookupClient = clientsManager.getLookupClient();
    }

    private RelationsConfig getKesselRelationsConfig() {
        RelationsConfig kesselRelationsConfig = new RelationsConfig() {
            @Override
            public boolean isSecureClients() {
                return recipientsResolverConfig.isKesselUseSecureClient();
            }

            @Override
            public String targetUrl() {
                return recipientsResolverConfig.getKesselTargetUrl();
            }

            @Override
            public Optional<AuthenticationConfig> authenticationConfig() {
                AuthenticationConfig authenticationConfig = new AuthenticationConfig() {
                    @Override
                    public org.project_kessel.clients.authn.AuthenticationConfig.AuthMode mode() {
                        return recipientsResolverConfig.getKesselClientMode();
                    }

                    @Override
                    public Optional<OIDCClientCredentialsConfig> clientCredentialsConfig() {
                        OIDCClientCredentialsConfig clientCredentialsConfig = new OIDCClientCredentialsConfig() {
                            @Override
                            public String issuer() {
                                return recipientsResolverConfig.getKesselClientIssuer().get();
                            }

                            @Override
                            public String clientId() {
                                return recipientsResolverConfig.getKesselClientId().get();
                            }

                            @Override
                            public String clientSecret() {
                                return recipientsResolverConfig.getKesselClientSecret().get();
                            }

                            @Override
                            public Optional<String[]> scope() {
                                return Optional.empty();
                            }

                            @Override
                            public Optional<String> oidcClientCredentialsMinterImplementation() {
                                return Optional.empty();
                            }
                        };

                        return Optional.of(clientCredentialsConfig);
                    }
                };

                return Optional.of(authenticationConfig);
            }
        };
        return kesselRelationsConfig;
    }

    public Set<String> lookupSubjects(RecipientsAuthorizationCriterion recipientsAuthorizationCriterion) {
        Set<String> userIds = new HashSet<>();
        LookupSubjectsRequest request = getLookupSubjectsRequest(recipientsAuthorizationCriterion);

        final String kesselAdditionalDomainName = String.format("%s/", recipientsResolverConfig.getKesselDomain());
        for (Iterator<LookupSubjectsResponse> it = lookupClient.lookupSubjects(request); it.hasNext();) {
            LookupSubjectsResponse response = it.next();
            userIds.add(response.getSubject().getSubject().getId().replaceAll(kesselAdditionalDomainName, ""));
        }
        Log.infof("Kessel returned %d user(s) for request %s", userIds.size(), request);
        return userIds;
    }

    private static LookupSubjectsRequest getLookupSubjectsRequest(RecipientsAuthorizationCriterion recipientsAuthorizationCriterion) {
        LookupSubjectsRequest request = LookupSubjectsRequest.newBuilder()
            .setResource(ObjectReference.newBuilder()
                .setType(ObjectType.newBuilder()
                    .setNamespace(recipientsAuthorizationCriterion.getType().getNamespace())
                    .setName(recipientsAuthorizationCriterion.getType().getName()).build())
                .setId(recipientsAuthorizationCriterion.getId())
                .build())
            .setRelation(recipientsAuthorizationCriterion.getRelation())
            .setSubjectType(ObjectType.newBuilder().setNamespace(RBAC_NAMESPACE).setName(SUBJECT_TYPE_USER).build())
            .build();
        return request;
    }
}
