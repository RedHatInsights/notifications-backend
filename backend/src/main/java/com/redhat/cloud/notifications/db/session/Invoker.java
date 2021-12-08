package com.redhat.cloud.notifications.db.session;

import org.jboss.logging.Logger;

import javax.persistence.PrePersist;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

class Invoker {

    private static final Logger LOGGER = Logger.getLogger(Invoker.class);
    private final Map<Class<?>, InvokerCache> cacheMap = new HashMap<>();

    private static class InvokerCache {
        private Method prePersist;
    }

    private <T> InvokerCache get(Class<T> aClass) {
        return cacheMap.computeIfAbsent(aClass, _aClass -> {
            InvokerCache cache = new InvokerCache();
            for (Method method: aClass.getDeclaredMethods()) {
                PrePersist prePersist = method.getAnnotation(PrePersist.class);
                if (prePersist != null) {
                    cache.prePersist = method;
                    break;
                }
            }

            return cache;
        });
    }

    public <T> void prePersist(T instance) {
        InvokerCache cache = get(instance.getClass());
        if (cache.prePersist != null) {
            try {
                cache.prePersist.invoke(instance);
            } catch (InvocationTargetException | IllegalAccessException exception) {
                LOGGER.warnf(exception, "Unable to call PrePersist method [%s] found in class [%s]", cache.prePersist.getName(), instance.getClass().getName());
            }
        }
    }
}
