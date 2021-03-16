package com.redhat.cloud.notifications.routers.models;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import java.util.ArrayList;
import java.util.List;

@JsonAutoDetect(fieldVisibility = Visibility.ANY)
public class SettingsValueJsonForm {

    private static String COMPONENT_LABEL = "plain-text";
    private static String COMPONENT_SUBSCRIPTION = "descriptiveCheckbox";

    @JsonAutoDetect(fieldVisibility = Visibility.ANY)
    public static class Field {
        public String name;
        public String label;
        @JsonInclude(Include.NON_NULL)
        public String description;
        @JsonInclude(Include.NON_NULL)
        public Object initialValue;
        public String component;
        @JsonInclude(Include.NON_NULL)
        public List<Object> validate;
        @JsonInclude(Include.NON_NULL)
        public List<Field> fields;
    }

    public List<Field> fields = new ArrayList<>();

    public static List<SettingsValueJsonForm> fromSettingsValue(SettingsValues values) {
        ArrayList<SettingsValueJsonForm> forms = new ArrayList<>();

        values.bundles.forEach((bundleName, bundleSettingsValue) -> {
            bundleSettingsValue.applications.forEach((applicationName, applicationSettingsValue) -> {
                SettingsValueJsonForm form = new SettingsValueJsonForm();
                forms.add(form);
                createLabelField(String.format("%s - %s", bundleSettingsValue.name, applicationSettingsValue.name), form.fields);

                applicationSettingsValue.notifications.forEach((emailSubscriptionType, isSubscribed) -> {
                    // Todo: Check if the bundle/application supports instant/daily emails and remove if they dont

                    Field field = new Field();
                    field.name = String.format("bundles[%s].applications[%s].notifications[%s]", bundleName, applicationName, emailSubscriptionType.toString());
                    field.initialValue = isSubscribed;
                    field.validate = List.of();
                    field.component = COMPONENT_SUBSCRIPTION;
                    switch (emailSubscriptionType) {
                        case DAILY:
                            field.label = "Daily digest";
                            field.description =  "Daily summary of triggered application events in 24 hours span. See notification settings for configuration.";
                            break;
                        case INSTANT:
                            field.label = "Instant notification";
                            field.description = "Immediate email for each triggered application event. See notification settings for configuration.";
                            break;
                        default:
                            return;
                    }
                    form.fields.add(field);
                });
            });
        });

        return forms;
    }

    private static Field createLabelField(String label, List<Field> parentContainer) {
        Field field = new Field();
        field.component = COMPONENT_LABEL;
        field.label = label;
        parentContainer.add(field);
        return field;
    }

}
