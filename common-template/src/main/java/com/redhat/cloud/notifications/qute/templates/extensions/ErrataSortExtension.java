package com.redhat.cloud.notifications.qute.templates.extensions;

import io.quarkus.logging.Log;
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
        Long id1 = extractIdIntValue(String.valueOf(m1.get("id")));
        Long id2 = extractIdIntValue(String.valueOf(m2.get("id")));
        return id2.compareTo(id1);
    }

    // Id format is like RHSA-2024:0315, we need to extract the numeric part to compare it
    private static Long extractIdIntValue(String id) {
        try {
            String yearPlusId = id.split("-")[1];
            String yearOnly = yearPlusId.split(":")[0];
            String idOnly = Integer.valueOf(yearPlusId.split(":")[1]).toString();
            // we have to pad the second part of id to avoid 2025:0001 to be considered as lower than 2024:0010
            String paddedId = String.format("%1$9s", idOnly).replace(' ', '0');

            return Long.valueOf(yearOnly + paddedId);
        } catch (Exception e) {
            Log.errorf("Error extracting id int: %s", id);
            return 0L;
        }
    }
}

