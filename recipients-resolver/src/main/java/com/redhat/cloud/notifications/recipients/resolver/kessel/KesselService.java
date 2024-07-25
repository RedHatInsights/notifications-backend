package com.redhat.cloud.notifications.recipients.resolver.kessel;

import com.redhat.cloud.notifications.recipients.config.RecipientsResolverConfig;
import com.redhat.cloud.notifications.recipients.model.ExternalAuthorizationCriteria;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.project_kessel.api.relations.v1beta1.LookupSubjectsRequest;
import org.project_kessel.api.relations.v1beta1.LookupSubjectsResponse;
import org.project_kessel.api.relations.v1beta1.ObjectReference;
import org.project_kessel.api.relations.v1beta1.ObjectType;
import org.project_kessel.relations.client.LookupClient;
import org.project_kessel.relations.client.RelationsGrpcClientsManager;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

@ApplicationScoped
public class KesselService {

    static final String SUBJECT_TYPE_USER = "user";

    @Inject
    RecipientsResolverConfig recipientsResolverConfig;

    LookupClient lookupClient;

    @PostConstruct
    void postConstruct() {
        RelationsGrpcClientsManager clientsManager;
        if (recipientsResolverConfig.isKesselUseSecureClient()) {
            clientsManager = RelationsGrpcClientsManager.forSecureClients(recipientsResolverConfig.getKesselTargetUrl());
        } else {
            clientsManager = RelationsGrpcClientsManager.forInsecureClients(recipientsResolverConfig.getKesselTargetUrl());
        }
        lookupClient = clientsManager.getLookupClient();
    }

    public Set<String> lookupSubjects(ExternalAuthorizationCriteria externalAuthorizationCriteria) {
        Set<String> userNames = new HashSet<>();
        LookupSubjectsRequest request = getLookupSubjectsRequest(externalAuthorizationCriteria);

        for (Iterator<LookupSubjectsResponse> it = lookupClient.lookupSubjects(request); it.hasNext();) {
            LookupSubjectsResponse response = it.next();
            userNames.add(response.getSubject().getSubject().getId());
        }
        return userNames;
    }

    private static LookupSubjectsRequest getLookupSubjectsRequest(ExternalAuthorizationCriteria externalAuthorizationCriteria) {
        LookupSubjectsRequest request = LookupSubjectsRequest.newBuilder()
            .setResource(ObjectReference.newBuilder()
                .setType(ObjectType.newBuilder().setName(externalAuthorizationCriteria.getAssetType()).build())
                .setId(externalAuthorizationCriteria.getAssetId())
                .build())
            .setRelation(externalAuthorizationCriteria.getRelation())
            .setSubjectType(ObjectType.newBuilder().setName(SUBJECT_TYPE_USER).build())
            .build();
        return request;
    }
}
