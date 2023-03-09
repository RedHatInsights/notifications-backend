package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.StatelessSessionFactory;
import com.redhat.cloud.notifications.models.IntegrationTemplate;
import com.redhat.cloud.notifications.models.Template;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class IntegrationTemplateRepositoryTest {

    public static final String SPECIFIC_TEMPLATE = "specific-template";
    public static final String INTEGRATION_TYPE = "test-integration-type";
    public static final String GENERIC_TEMPLATE = "generic-template";

    @Inject
    EntityManager entityManager;

    @Inject
    TemplateRepository templateRepository;

    @Inject
    StatelessSessionFactory statelessSessionFactory;

    @Test
    void testMostSpecificOneIsUsed() {
        statelessSessionFactory.withSession(statelessSession -> {

            Template specificTemplate = createTemplate(SPECIFIC_TEMPLATE, "Just a test", "Li la lu");
            createIntegrationTemplate(specificTemplate, IntegrationTemplate.TemplateKind.APPLICATION, INTEGRATION_TYPE);
            Template genericTemplate = createTemplate(GENERIC_TEMPLATE, "The default", "The default");
            createIntegrationTemplate(genericTemplate, IntegrationTemplate.TemplateKind.DEFAULT, INTEGRATION_TYPE);

            Optional<IntegrationTemplate> ito = templateRepository.findIntegrationTemplate(null, null, IntegrationTemplate.TemplateKind.APPLICATION, INTEGRATION_TYPE);
            assertTrue(ito.isPresent());
            IntegrationTemplate it = ito.get();
            assertEquals(it.getTemplateKind(), IntegrationTemplate.TemplateKind.APPLICATION);
            assertEquals(specificTemplate.getName(), it.getTheTemplate().getName());

            // This should be the default
            ito = templateRepository.findIntegrationTemplate(null, null, IntegrationTemplate.TemplateKind.DEFAULT, INTEGRATION_TYPE);
            assertTrue(ito.isPresent());
            it = ito.get();
            assertEquals(it.getTemplateKind(), IntegrationTemplate.TemplateKind.DEFAULT);
            assertEquals(genericTemplate.getName(), it.getTheTemplate().getName());

            deleteTemplates();

        });
    }

    @Test
    void testUserFallback() {
        statelessSessionFactory.withSession(statelessSession -> {

            Template specificTemplate = createTemplate(SPECIFIC_TEMPLATE, "Just a test", "Li la lu");
            createIntegrationTemplate(specificTemplate, IntegrationTemplate.TemplateKind.ORG, INTEGRATION_TYPE, "user-123", "org-id-123");
            Template genericTemplate = createTemplate(GENERIC_TEMPLATE, "The default", "The default");
            createIntegrationTemplate(genericTemplate, IntegrationTemplate.TemplateKind.DEFAULT, INTEGRATION_TYPE);

            Optional<IntegrationTemplate> ito = templateRepository.findIntegrationTemplate(null,
                    "org-id-123", IntegrationTemplate.TemplateKind.ORG, INTEGRATION_TYPE);
            assertTrue(ito.isPresent());
            IntegrationTemplate it = ito.get();
            assertEquals(IntegrationTemplate.TemplateKind.ORG, it.getTemplateKind());
            assertEquals(specificTemplate.getName(), it.getTheTemplate().getName());

            // This should be the default
            ito = templateRepository.findIntegrationTemplate(null, null, IntegrationTemplate.TemplateKind.DEFAULT, INTEGRATION_TYPE);
            assertTrue(ito.isPresent());
            it = ito.get();
            assertEquals(IntegrationTemplate.TemplateKind.DEFAULT, it.getTemplateKind());
            assertEquals(genericTemplate.getName(), it.getTheTemplate().getName());

            // Now see if we fall back to default if the user has no template
            ito = templateRepository.findIntegrationTemplate(null,
                    "unknown-org-id", IntegrationTemplate.TemplateKind.ORG, INTEGRATION_TYPE);
            assertTrue(ito.isPresent());
            it = ito.get();
            assertEquals(IntegrationTemplate.TemplateKind.DEFAULT, it.getTemplateKind());
            assertEquals(genericTemplate.getName(), it.getTheTemplate().getName());

            deleteTemplates();

        });
    }

    @Transactional
    Template createTemplate(String name, String description, String data) {
        Template template = new Template();
        template.setName(name);
        template.setDescription(description);
        template.setData(data);
        entityManager.persist(template);
        return template;
    }

    @Transactional
    IntegrationTemplate createIntegrationTemplate(Template template, IntegrationTemplate.TemplateKind kind, String iType, String account, String orgId) {
        IntegrationTemplate gt = new IntegrationTemplate();
        gt.setTemplateKind(kind);
        gt.setIntegrationType(iType);
        gt.setTheTemplate(template);
        if (account != null) {
            gt.setAccountId(account);
        }
        if (orgId != null) {
            gt.setOrgId(orgId);
        }
        entityManager.persist(gt);
        return gt;
    }

    IntegrationTemplate createIntegrationTemplate(Template template, IntegrationTemplate.TemplateKind kind, String iType) {
        return createIntegrationTemplate(template, kind, iType, null, null);
    }

    @Transactional
    void deleteTemplates() {
        String hql = "DELETE FROM Template t WHERE EXISTS (" +
                "SELECT 1 FROM IntegrationTemplate it " +
                "WHERE it.theTemplate = t AND it.integrationType = :integrationType)";
        entityManager
                .createQuery(hql)
                .setParameter("integrationType", INTEGRATION_TYPE)
                .executeUpdate();
    }
}
