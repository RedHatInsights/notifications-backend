package com.redhat.cloud.notifications;

import java.lang.reflect.Field;

public class ReflectionHelper {

    public static <T> void updateField(T object, String field, Object value, Class<T> klass) {
        try {
            Field bopUrlField = klass.getDeclaredField(field);
            bopUrlField.setAccessible(true);
            bopUrlField.set(object, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            String error = String.format("Error while updating value of field: [%s] to [%s].", field, value);

            if (e instanceof NoSuchFieldException) {
                if (object.getClass() == klass) {
                    error += "\nTry specifying the class you want instead of using theObject.getClass(). Mocked objects use a different class.";
                }
            }
            throw new RuntimeException(error, e);
        }
    }
}
