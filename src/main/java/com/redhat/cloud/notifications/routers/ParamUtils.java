package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.db.Query;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;

public class ParamUtils {
    public static final String PAGE_SIZE = "pageSize";
    public static final String PAGE_NUMBER = "pageNumber";

    public static Query.Limit parseQueryParams(UriInfo uriInfo) {
        MultivaluedMap<String, String> queryParameters = uriInfo.getQueryParameters();
        String pageSizeS = queryParameters.getFirst(PAGE_SIZE);
        if (pageSizeS != null) {
            int pageSize = Integer.parseInt(pageSizeS);
            int pageNumber = 0;

            String pageNumberS = queryParameters.getFirst(PAGE_NUMBER);
            if (pageNumberS != null) {
                pageNumber = Integer.parseInt(pageNumberS);
            }

            return new Query.Limit(pageNumber, pageSize);
        }
        return new Query.Limit(0, 0);
    }
}
