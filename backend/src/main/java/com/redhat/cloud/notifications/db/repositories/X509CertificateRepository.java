package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.X509Certificate;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class X509CertificateRepository {

    @Inject
    EntityManager entityManager;

    @Inject
    ApplicationRepository applicationRepository;

    @Transactional
    public X509Certificate createCertificate(X509Certificate gatewayCertificate) {
        Application application = applicationRepository.getApplication(gatewayCertificate.getBundle(), gatewayCertificate.getApplication());
        gatewayCertificate.setCertificateApplication(application);
        entityManager.persist(gatewayCertificate);
        return gatewayCertificate;
    }


    public Optional<X509Certificate> findCertificate(String bundle, String application, String subjectDn) {
        final String query = "SELECT gc FROM X509Certificate gc where gc.certificateApplication.bundle.name = :bundle " +
            "AND gc.certificateApplication.name = :application " +
            "AND gc.subjectDn = :subjectDn";
        try {
            return Optional.of(this.entityManager
                .createQuery(query, X509Certificate.class)
                .setParameter("bundle", bundle)
                .setParameter("application", application)
                .setParameter("subjectDn", subjectDn)
                .getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    @Transactional
    public boolean updateCertificate(UUID id, X509Certificate gatewayCertificate) {
        String hql = "UPDATE X509Certificate SET subjectDn = :subjectDn, sourceEnvironment = :sourceEnvironment WHERE id = :id";
        int rowCount = entityManager.createQuery(hql)
                .setParameter("subjectDn", gatewayCertificate.getSubjectDn())
                .setParameter("sourceEnvironment", gatewayCertificate.getSourceEnvironment())
                .setParameter("id", id)
                .executeUpdate();
        return rowCount > 0;
    }

    @Transactional
    public boolean deleteCertificate(UUID id) {
        X509Certificate certificate = entityManager.find(X509Certificate.class, id);
        if (certificate == null) {
            return false;
        } else {
            String deleteHql = "DELETE FROM X509Certificate WHERE id = :id";
            int rowCount = entityManager.createQuery(deleteHql)
                    .setParameter("id", id)
                    .executeUpdate();
            return rowCount > 0;
        }
    }
}
