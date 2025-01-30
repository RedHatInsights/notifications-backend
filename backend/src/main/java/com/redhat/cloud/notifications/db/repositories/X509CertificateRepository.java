package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.X509Certificate;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;
import org.hibernate.query.Query;
import java.util.List;
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

    public List<X509Certificate> findCertificates() {
        final String query = "select id, subjectDn, sourceEnvironment, certificateApplication.bundle.name, certificateApplication.name FROM X509Certificate";
        return this.entityManager
            .createQuery(query)
            .unwrap(Query.class)
            .setTupleTransformer(
                (tuple, aliases) -> {
                    X509Certificate certificateData = new X509Certificate();
                    certificateData.setId((UUID) tuple[0]);
                    certificateData.setSubjectDn((String) tuple[1]);
                    certificateData.setSourceEnvironment((String) tuple[2]);
                    certificateData.setBundle((String) tuple[3]);
                    certificateData.setApplication((String) tuple[4]);
                    return certificateData;
                }
            )
            .getResultList();
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
        String deleteHql = "DELETE FROM X509Certificate WHERE id = :id";
        int rowCount = entityManager.createQuery(deleteHql)
                .setParameter("id", id)
                .executeUpdate();
        return rowCount > 0;
    }
}
