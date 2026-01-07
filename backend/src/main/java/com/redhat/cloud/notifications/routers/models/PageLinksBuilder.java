package com.redhat.cloud.notifications.routers.models;

import com.redhat.cloud.notifications.db.Query;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PageLinksBuilder {

    public static Map<String, String> build(String apiPath, long count, Query query) {
        return build(apiPath, count, query.getLimit().getLimit(), query.getLimit().getOffset());
    }

    // New method that preserves query parameters
    public static Map<String, String> build(UriInfo uriInfo, long count, Query query) {
        return build(uriInfo, count, query.getLimit().getLimit(), query.getLimit().getOffset());
    }

    // New method that preserves query parameters
    public static Map<String, String> build(UriInfo uriInfo, long count, long limit, long currentOffset) {
        String apiPath = uriInfo.getPath();
        MultivaluedMap<String, String> queryParams = uriInfo.getQueryParameters();

        // Build query string with all parameters except limit and offset
        StringBuilder queryString = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : queryParams.entrySet()) {
            String paramName = entry.getKey();
            // Skip limit and offset as we'll add them dynamically
            if (!paramName.equals("limit") && !paramName.equals("offset")) {
                for (String value : entry.getValue()) {
                    if (queryString.length() > 0) {
                        queryString.append("&");
                    }
                    queryString.append(paramName).append("=").append(value);
                }
            }
        }

        String baseLink;
        if (queryString.length() > 0) {
            baseLink = apiPath + "?" + queryString + "&limit=" + limit + "&offset=";
        } else {
            baseLink = apiPath + "?limit=" + limit + "&offset=";
        }

        return buildLinks(baseLink, count, limit, currentOffset);
    }

    public static Map<String, String> build(String apiPath, long count, long limit, long currentOffset) {
        String baseLink = apiPath + "?limit=" + limit + "&offset=";
        return buildLinks(baseLink, count, limit, currentOffset);
    }

    private static Map<String, String> buildLinks(String baseLink, long count, long limit, long currentOffset) {
        Map<String, String> links = new HashMap<>();

        // first
        links.put("first", baseLink + "0");

        // last
        long lastOffset;
        if (limit >= count) {
            lastOffset = 0;
        } else {
            long modulo = count % limit;
            if (modulo == 0) {
                modulo = limit;
            }
            lastOffset = count - (limit == 1 ? 1 : modulo);
        }
        links.put("last", baseLink + lastOffset);

        // prev
        if (currentOffset > 0) {
            long prevOffset = currentOffset - limit;
            if (prevOffset < 0) {
                prevOffset = 0;
            }
            links.put("prev", baseLink + prevOffset);
        }

        // next
        if (currentOffset < lastOffset) {
            long nextOffset = currentOffset + limit;
            links.put("next", baseLink + nextOffset);
        }

        return links;
    }
}
