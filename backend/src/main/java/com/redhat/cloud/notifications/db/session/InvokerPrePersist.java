package com.redhat.cloud.notifications.db.session;

import javax.persistence.PrePersist;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * This class deals with calls that the stateful session do but the stateless session does not.
 *
 * For example, the `@PrePersist` annotation.
 *
 * It heavily relies on introspection to find what methods have the required annotation.
 * To lessen this burden, a cache is created per class once, so the methods are not inspected all the time.
 */
class InvokerPrePersist {

    private final Map<Class<?>, Optional<Method>> cacheMap = new HashMap<>();

    private <T> Optional<Method> get(Class<T> aClass) {
        return cacheMap.computeIfAbsent(aClass, _aClass -> {
            for (Method method: aClass.getMethods()) {
                if (method.isAnnotationPresent(PrePersist.class)) {
                    return Optional.of(method);
                }
            }

            return Optional.empty();
        });
    }

    public <T> void prePersist(T instance) {
        Optional<Method> cache = get(instance.getClass());
        if (cache.isPresent()) {
            try {
                cache.get().invoke(instance);
            } catch (InvocationTargetException | IllegalAccessException exception) {
                throw new RuntimeException(String.format(
                    "Unable to call PrePersist method [%s] found in class [%s]", cache.get().getName(), instance.getClass().getName()
                ), exception);
            }
        }
    }
}
