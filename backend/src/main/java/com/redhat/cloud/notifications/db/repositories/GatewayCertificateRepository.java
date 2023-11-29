package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.GatewayCertificate;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import java.util.UUID;

@ApplicationScoped
public class GatewayCertificateRepository {

    @Inject
    EntityManager entityManager;

    @Inject
    ApplicationRepository applicationRepository;

    @Transactional
    public GatewayCertificate createGatewayCertificate(GatewayCertificate gatewayCertificate) {
        Application application = applicationRepository.getApplication(gatewayCertificate.getBundle(), gatewayCertificate.getApplication());
        gatewayCertificate.setGatewayCertificateApplication(application);
        entityManager.persist(gatewayCertificate);
        return gatewayCertificate;
    }


    public GatewayCertificate findGatewayCertificate(String bundle, String application, String certificateData) {
        final String query = "SELECT gc FROM GatewayCertificate gc where gc.gatewayCertificateApplication.bundle.name = :bundle " +
            "AND gc.gatewayCertificateApplication.name = :application " +
            "AND gc.certificateData = :certificateData";

        return this.entityManager
            .createQuery(query, GatewayCertificate.class)
            .setParameter("bundle", bundle)
            .setParameter("application", application)
            .setParameter("certificateData", certificateData)
            .setMaxResults(1)
            .getResultList()
            .stream()
            .findFirst()
            .orElse(null);
    }

    @Transactional
    public boolean updateGatewayCertificate(UUID id, GatewayCertificate gatewayCertificate) {
        String hql = "UPDATE GatewayCertificate SET certificateData = :certificateData, environment = :environment WHERE id = :id";
        int rowCount = entityManager.createQuery(hql)
                .setParameter("certificateData", gatewayCertificate.getCertificateData())
                .setParameter("environment", gatewayCertificate.getEnvironment())
                .setParameter("id", id)
                .executeUpdate();
        return rowCount > 0;
    }

    @Transactional
    public boolean deleteGatewayCertificate(UUID id) {
        GatewayCertificate gatewayCertificate = entityManager.find(GatewayCertificate.class, id);
        if (gatewayCertificate == null) {
            throw new NotFoundException("Gateway certificate not found");
        } else {
            String deleteHql = "DELETE FROM GatewayCertificate WHERE id = :id";
            int rowCount = entityManager.createQuery(deleteHql)
                    .setParameter("id", id)
                    .executeUpdate();
            return rowCount > 0;
        }
    }
}
