package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.db.StatelessSessionFactory;
import com.redhat.cloud.notifications.models.Template;
import io.quarkus.logging.Log;
import io.quarkus.qute.TemplateLocator;
import io.quarkus.qute.Variant;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.NoResultException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Optional;

@ApplicationScoped
public class DbTemplateLocator implements TemplateLocator {

    @Inject
    StatelessSessionFactory statelessSessionFactory;

    @Override
    public Optional<TemplateLocation> locate(String name) {
        String hql = "FROM Template WHERE name = :name";
        try {
            Template template = statelessSessionFactory.getCurrentSession().createQuery(hql, Template.class)
                    .setParameter("name", name)
                    .getSingleResult();
            Log.tracef("Template with [name=%s] found in the database", name);
            return Optional.of(buildTemplateLocation(template.getData()));
        } catch (NoResultException e) {
            Log.tracef("Template with [name=%s] not found in the database", name);
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
