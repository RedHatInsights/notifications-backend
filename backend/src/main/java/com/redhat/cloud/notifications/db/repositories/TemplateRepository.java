package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.models.AggregationEmailTemplate;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.InstantEmailTemplate;
import com.redhat.cloud.notifications.models.Template;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.TypedQuery;
import javax.transaction.Transactional;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class TemplateRepository {

    private static final String SUBJECT_NOT_FOUND = "Subject template not found";
    private static final String BODY_NOT_FOUND = "Body template not found";

    @Inject
    EntityManager entityManager;

    @Transactional
    public Template createTemplate(Template template) {
        entityManager.persist(template);
        return template;
    }

    public List<Template> findAllTemplates() {
        String hql = "FROM Template";
        return entityManager.createQuery(hql, Template.class)
                .getResultList();
    }

    public Template findTemplateById(UUID id) {
        return findTemplate(id, "Template not found");
    }

    @Transactional
    public boolean updateTemplate(UUID id, Template template) {
        String hql = "UPDATE Template SET name = :name, description = :description, data = :data WHERE id = :id";
        int rowCount = entityManager.createQuery(hql)
                .setParameter("name", template.getName())
                .setParameter("description", template.getDescription())
                .setParameter("data", template.getData())
                .setParameter("id", id)
                .executeUpdate();
        return rowCount > 0;
    }

    @Transactional
    public boolean deleteTemplate(UUID id) {
        Template template = entityManager.find(Template.class, id);
        if (template == null) {
            throw new NotFoundException("Template not found");
        } else {
            String checkHql = "SELECT COUNT(*) FROM Template WHERE id != :id AND data LIKE :include";
            long count = entityManager.createQuery(checkHql, Long.class)
                    .setParameter("id", id)
                    .setParameter("include", "%{#include " + template.getName() + "%")
                    .getSingleResult();
            if (count > 0) {
                throw new BadRequestException("Included templates can't be deleted, remove the inclusion or delete the outer template first");
            } else {
                String deleteHql = "DELETE FROM Template WHERE id = :id";
                int rowCount = entityManager.createQuery(deleteHql)
                        .setParameter("id", id)
                        .executeUpdate();
                return rowCount > 0;
            }
        }
    }

    @Transactional
    public InstantEmailTemplate createInstantEmailTemplate(InstantEmailTemplate template) {
        Template subjectTemplate = findTemplate(template.getSubjectTemplateId(), SUBJECT_NOT_FOUND);
        Template bodyTemplate = findTemplate(template.getBodyTemplateId(), BODY_NOT_FOUND);

        template.setSubjectTemplate(subjectTemplate);
        template.setBodyTemplate(bodyTemplate);

        if (template.getEventTypeId() != null) {
            EventType eventType = findEventType(template.getEventTypeId());
            template.setEventType(eventType);
        }

        entityManager.persist(template);

        // The full event type isn't needed in the REST response.
        template.filterOutEventType();
        // The full templates aren't needed in the REST response.
        template.filterOutTemplates();

        return template;
    }

    public List<InstantEmailTemplate> findAllInstantEmailTemplates(UUID applicationId) {
        String hql = "FROM InstantEmailTemplate t LEFT JOIN FETCH t.eventType";

        if (applicationId != null) {
            hql += " WHERE t.eventType.application.id = :applicationId";
        }

        TypedQuery<InstantEmailTemplate> query = entityManager.createQuery(hql, InstantEmailTemplate.class);
        if (applicationId != null) {
            query.setParameter("applicationId", applicationId);
        }

        List<InstantEmailTemplate> instantEmailTemplates = query.getResultList();
        for (InstantEmailTemplate instantEmailTemplate : instantEmailTemplates) {
            if (instantEmailTemplate.getEventType() != null) {
                // We need the event types in the REST response, but not their parent application.
                instantEmailTemplate.getEventType().filterOutApplication();
            }
            // The full templates aren't needed in the REST response.
            instantEmailTemplate.filterOutTemplates();
        }
        return instantEmailTemplates;
    }

    public List<InstantEmailTemplate> findInstantEmailTemplatesByEventType(UUID eventTypeId) {
        String hql = "FROM InstantEmailTemplate t JOIN FETCH t.eventType et WHERE et.id = :eventTypeId";
        List<InstantEmailTemplate> instantEmailTemplates = entityManager.createQuery(hql, InstantEmailTemplate.class)
                .setParameter("eventTypeId", eventTypeId)
                .getResultList();
        for (InstantEmailTemplate instantEmailTemplate : instantEmailTemplates) {
            // The full event types aren't needed in the REST response.
            instantEmailTemplate.filterOutEventType();
            // The full templates aren't needed in the REST response.
            instantEmailTemplate.filterOutTemplates();
        }
        return instantEmailTemplates;
    }

    public InstantEmailTemplate findInstantEmailTemplateById(UUID id) {
        String hql = "FROM InstantEmailTemplate t JOIN FETCH t.subjectTemplate JOIN FETCH t.bodyTemplate " +
                "LEFT JOIN FETCH t.eventType WHERE t.id = :id";
        try {
            InstantEmailTemplate template = entityManager.createQuery(hql, InstantEmailTemplate.class)
                    .setParameter("id", id)
                    .getSingleResult();
            // The full event type isn't needed in the REST response.
            template.filterOutEventType();
            return template;
        } catch (NoResultException e) {
            throw new NotFoundException("Instant email template not found");
        }
    }

    @Transactional
    public boolean updateInstantEmailTemplate(UUID id, InstantEmailTemplate template) {
        String hql = "UPDATE InstantEmailTemplate SET eventType = :eventType, subjectTemplate = :subjectTemplate, " +
                "bodyTemplate = :bodyTemplate WHERE id = :id";

        EventType eventType;
        if (template.getEventTypeId() == null) {
            eventType = null;
        } else {
            eventType = findEventType(template.getEventTypeId());
        }
        Template subjectTemplate = findTemplate(template.getSubjectTemplateId(), SUBJECT_NOT_FOUND);
        Template bodyTemplate = findTemplate(template.getBodyTemplateId(), BODY_NOT_FOUND);

        int rowCount = entityManager.createQuery(hql)
                .setParameter("eventType", eventType)
                .setParameter("subjectTemplate", subjectTemplate)
                .setParameter("bodyTemplate", bodyTemplate)
                .setParameter("id", id)
                .executeUpdate();
        return rowCount > 0;
    }

    @Transactional
    public boolean deleteInstantEmailTemplate(UUID id) {
        String hql = "DELETE FROM InstantEmailTemplate WHERE id = :id";
        int rowCount = entityManager.createQuery(hql)
                .setParameter("id", id)
                .executeUpdate();
        return rowCount > 0;
    }

    @Transactional
    public AggregationEmailTemplate createAggregationEmailTemplate(AggregationEmailTemplate template) {
        Template subjectTemplate = findTemplate(template.getSubjectTemplateId(), SUBJECT_NOT_FOUND);
        Template bodyTemplate = findTemplate(template.getBodyTemplateId(), BODY_NOT_FOUND);

        template.setSubscriptionType(template.getSubscriptionType());
        template.setSubjectTemplate(subjectTemplate);
        template.setBodyTemplate(bodyTemplate);

        if (template.getApplicationId() != null) {
            Application app = findApplication(template.getApplicationId());
            template.setApplication(app);
        }

        entityManager.persist(template);

        // The full application isn't needed in the REST response.
        template.filterOutApplication();
        // The full templates aren't needed in the REST response.
        template.filterOutTemplates();

        return template;
    }

    public List<AggregationEmailTemplate> findAllAggregationEmailTemplates() {
        String hql = "FROM AggregationEmailTemplate t LEFT JOIN FETCH t.application";
        List<AggregationEmailTemplate> aggregationEmailTemplates = entityManager.createQuery(hql, AggregationEmailTemplate.class)
                .getResultList();
        for (AggregationEmailTemplate aggregationEmailTemplate : aggregationEmailTemplates) {
            // The full templates aren't needed in the REST response.
            aggregationEmailTemplate.filterOutTemplates();
        }
        return aggregationEmailTemplates;
    }

    public List<AggregationEmailTemplate> findAggregationEmailTemplatesByApplication(UUID appId) {
        String hql = "FROM AggregationEmailTemplate t JOIN FETCH t.application a WHERE a.id = :appId";
        List<AggregationEmailTemplate> aggregationEmailTemplates = entityManager.createQuery(hql, AggregationEmailTemplate.class)
                .setParameter("appId", appId)
                .getResultList();
        for (AggregationEmailTemplate aggregationEmailTemplate : aggregationEmailTemplates) {
            // The full application isn't needed in the REST response.
            aggregationEmailTemplate.filterOutApplication();
            // The full templates aren't needed in the REST response.
            aggregationEmailTemplate.filterOutTemplates();
        }
        return aggregationEmailTemplates;
    }

    public AggregationEmailTemplate findAggregationEmailTemplateById(UUID id) {
        String hql = "FROM AggregationEmailTemplate t JOIN FETCH t.subjectTemplate JOIN FETCH t.bodyTemplate " +
                "LEFT JOIN FETCH t.application WHERE t.id = :id";
        try {
            AggregationEmailTemplate template = entityManager.createQuery(hql, AggregationEmailTemplate.class)
                    .setParameter("id", id)
                    .getSingleResult();
            // The full application isn't needed in the REST response.
            template.filterOutApplication();
            return template;
        } catch (NoResultException e) {
            throw new NotFoundException("Aggregation email template not found");
        }
    }

    @Transactional
    public boolean updateAggregationEmailTemplate(UUID id, AggregationEmailTemplate template) {
        String hql = "UPDATE AggregationEmailTemplate SET application = :app, subjectTemplate = :subjectTemplate, " +
                "subscriptionType = :subscriptionType, bodyTemplate = :bodyTemplate WHERE id = :id";

        Application app;
        if (template.getApplicationId() == null) {
            app = null;
        } else {
            app = findApplication(template.getApplicationId());
        }
        Template subjectTemplate = findTemplate(template.getSubjectTemplateId(), SUBJECT_NOT_FOUND);
        Template bodyTemplate = findTemplate(template.getBodyTemplateId(), BODY_NOT_FOUND);

        int rowCount = entityManager.createQuery(hql)
                .setParameter("app", app)
                .setParameter("subscriptionType", template.getSubscriptionType())
                .setParameter("subjectTemplate", subjectTemplate)
                .setParameter("bodyTemplate", bodyTemplate)
                .setParameter("id", id)
                .executeUpdate();
        return rowCount > 0;
    }

    @Transactional
    public boolean deleteAggregationEmailTemplate(UUID id) {
        String hql = "DELETE FROM AggregationEmailTemplate WHERE id = :id";
        int rowCount = entityManager.createQuery(hql)
                .setParameter("id", id)
                .executeUpdate();
        return rowCount > 0;
    }

    private EventType findEventType(UUID id) {
        EventType eventType = entityManager.find(EventType.class, id);
        if (eventType == null) {
            throw new NotFoundException("Event type not found");
        }
        return eventType;
    }

    private Application findApplication(UUID id) {
        Application app = entityManager.find(Application.class, id);
        if (app == null) {
            throw new NotFoundException("Application not found");
        }
        return app;
    }

    private Template findTemplate(UUID id, String notFoundMessage) {
        Template template = entityManager.find(Template.class, id);
        if (template == null) {
            throw new NotFoundException(notFoundMessage);
        }
        return template;
    }
}
