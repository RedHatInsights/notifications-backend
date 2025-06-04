package com.redhat.cloud.notifications.qute.templates.extensions;

import io.quarkus.qute.TemplateExtension;
import java.util.List;
import java.util.Map;

public class ErrataSortExtension {

    private static final Map<String, Integer> severityOrder = Map.of(
        "Important", 0,
        "Moderate", 1,
        "Low", 2
    );

    /* Sort errata security array by security level, then by id */
    @TemplateExtension
    public static List<Map<String, Object>> sortErrataSecurityArray(List<Map<String, Object>> list) {
        list.sort((m1, m2) -> {
            String sev1 = String.valueOf(m1.get("severity"));
            String sev2 = String.valueOf(m2.get("severity"));

            int order1 = severityOrder.getOrDefault(sev1, Integer.MAX_VALUE);
            int order2 = severityOrder.getOrDefault(sev2, Integer.MAX_VALUE);

            int cmp = Integer.compare(order1, order2);
            if (cmp != 0) {
                return cmp;
            }

            return sortById(m1, m2);
        });

        return list;
    }

    /* Sort errata array by id */
    @TemplateExtension
    public static List<Map<String, Object>> sortErrataArray(List<Map<String, Object>> list) {
        list.sort(ErrataSortExtension::sortById);
        return list;
    }

    private static int sortById(Map<String, Object> m1, Map<String, Object> m2) {
        Integer name1 = extractIdIntValue(String.valueOf(m1.get("id")));
        Integer name2 = extractIdIntValue(String.valueOf(m2.get("id")));
        return name2.compareTo(name1);
    }

    private static int extractIdIntValue(String id) {
        return Integer.parseInt(id.split("-")[1].replace(":", ""));
    }
}

