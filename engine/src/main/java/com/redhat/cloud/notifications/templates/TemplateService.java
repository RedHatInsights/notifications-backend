package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.Environment;
import com.redhat.cloud.notifications.recipients.User;
import io.quarkus.qute.Engine;
import io.quarkus.qute.EvalContext;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.qute.ValueResolver;
import io.quarkus.scheduler.Scheduled;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.function.BiFunction;
import java.util.function.Function;

@ApplicationScoped
public class TemplateService {

    @Inject
    Engine engine;

    @Inject
    Environment environment;

    @Inject
    DbTemplateLocator dbTemplateLocator;

    @Inject
    FeatureFlipper featureFlipper;

    /*
     * When a DB template is modified (edited or deleted), its old version may still be included into another template
     * because the Qute engine has an internal cache. This scheduled method clears that cache periodically. We may want
     * to replace this with a better solution based on a Kafka topic and message broadcasting to all engine pods later.
     */
    @Scheduled(every = "${notifications.template-service.scheduled-clear.period:5m}", delayed = "${notifications.template-service.scheduled-clear.initial-delay:5m}")
    public void clearTemplates() {
        if (featureFlipper.isUseTemplatesFromDb()) {
            engine.clearTemplates();
        }
    }

    public TemplateInstance compileTemplate(String template, String name) {
        return engine.parse(template, null, name).instance();
    }

    public String renderTemplate(User user, Action action, TemplateInstance templateInstance) {
        return templateInstance
                .data("action", action)
                .data("user", user)
                .data("environment", environment)
                .render();
    }

    private static <T> ValueResolver buildValueResolver(Class<T> baseClass, String extensionName, Function<T, Object> valueTransformer) {
        return ValueResolver.builder()
                .applyToBaseClass(baseClass)
                .applyToName(extensionName)
                .resolveSync(new Function<EvalContext, Object>() {
                    @Override
                    public Object apply(EvalContext evalContext) {
                        return valueTransformer.apply((T) evalContext.getBase());
                    }
                })
                .build();
    }

    private static <T> ValueResolver buildAnyNameFunctionResolver(Class<T> baseClass, BiFunction<T, String, Object> valueTransformer) {
        return ValueResolver.builder()
                .applyToBaseClass(baseClass)
                .resolveSync(new Function<EvalContext, Object>() {
                    @Override
                    public Object apply(EvalContext evalContext) {
                        return valueTransformer.apply((T) evalContext.getBase(), evalContext.getName());
                    }
                })
                .build();
    }
}
