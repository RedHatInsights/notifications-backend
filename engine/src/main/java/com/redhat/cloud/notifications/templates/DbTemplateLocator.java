package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.models.Template;
import io.quarkus.qute.TemplateLocator;
import io.quarkus.qute.Variant;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Optional;

@ApplicationScoped
public class DbTemplateLocator implements TemplateLocator {

    private static Logger LOGGER = Logger.getLogger(DbTemplateLocator.class);

    @Inject
    EntityManager entityManager;

    @Override
    public Optional<TemplateLocation> locate(String name) {
        String hql = "FROM Template WHERE name = :name";
        try {
            Template template = entityManager.createQuery(hql, Template.class)
                    .setParameter("name", name)
                    .getSingleResult();
            LOGGER.tracef("Template with [name=%s] found in the database", name);
            return Optional.of(buildTemplateLocation(template.getData()));
        } catch (NoResultException e) {
            LOGGER.tracef("Template with [name=%s] not found in the database", name);
            return Optional.empty();
        }
    }

    private TemplateLocation buildTemplateLocation(String templateData) {

        return new TemplateLocation() {

            @Override
            public Reader read() {
                return new StringReader(templateData);
            }

            @Override
            public Optional<Variant> getVariant() {
                return Optional.empty();
            }
        };
    }
}
