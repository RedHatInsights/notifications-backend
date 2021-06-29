package com.redhat.cloud.notifications.routers.models;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import java.util.ArrayList;
import java.util.List;

@JsonAutoDetect(fieldVisibility = Visibility.ANY)
public class SettingsValueJsonForm {

    // These component names depends on the values used by UserPreferences UI
    private static String COMPONENT_LABEL = "plain-text";
    private static String COMPONENT_SUBSCRIPTION = "descriptiveCheckbox";
    private static String COMPONENT_SECTION = "section";

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

    @JsonAutoDetect(fieldVisibility = Visibility.ANY)
    public static class Sections extends Field {
        public List<Field> sections = new ArrayList<>();

        Sections() {
            name = "notification-preferences";
            component = COMPONENT_SECTION;
        }
    }

    public List<Sections> fields = new ArrayList<>();

    /**
     * This function is in charge of transforming a `SettingsValue` into a data-driven-form [1] object used to render
     * used by the user-preferences UI.
     *
     * Todo: It should be possible to simplify this with qute
     * [1] https://data-driven-forms.org/
     */
    public static SettingsValueJsonForm fromSettingsValue(SettingsValues values) {
        SettingsValueJsonForm form = new SettingsValueJsonForm();
        Sections sections = new Sections();
        form.fields.add(sections);

        values.bundles.forEach((bundleName, bundleSettingsValue) -> {
            bundleSettingsValue.applications.forEach((applicationName, applicationSettingsValue) -> {
                Field section = new Field();
                section.label = applicationSettingsValue.displayName;
                section.name = applicationName;
                section.fields = new ArrayList<>();

                sections.sections.add(section);
                Field fieldsField = new Field();
                fieldsField.fields = new ArrayList<>();
                section.fields.add(fieldsField);

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
                    fieldsField.fields.add(field);
                });
            });
        });

        return form;
    }

}
