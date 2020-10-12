package com.redhat.cloud.notifications.db;

import javax.ws.rs.QueryParam;

public class Query {
    @QueryParam("limit")
    private Integer pageSize;

    @QueryParam("pageNumber")
    private Integer pageNumber;

    @QueryParam("offset")
    private Integer offset;

    public static class Limit {
        private int pageNumber;
        private int pageSize;
        private int offset;

        public Limit(int pageNumber, int pageSize) {
            this.pageNumber = pageNumber;
            this.pageSize = pageSize;
        }

        public int getPageSize() {
            return pageSize;
        }

        public int calculateOffset() {
            if(offset > 0) {
                return offset;
            }
            return pageNumber * pageSize;
        }
    }

    public Limit getLimit() {
        if (pageSize != null) {
            if (pageNumber == null) {
                pageNumber = 0;
            }
            return new Limit(pageNumber, pageSize);
        }
        return new Limit(0, 0);
    }

    public static String getPostgresQuery(Limit limiter) {
        StringBuilder builder = new StringBuilder();
        builder.append("LIMIT ");
        builder.append(limiter.getPageSize());
        builder.append(" OFFSET ");
        builder.append(limiter.calculateOffset());
        return builder.toString();
    }

    public static String modifyQuery(String basicQuery, Query.Limit limiter) {
        // TODO Should we have sorting here (other than natural sort)
        if (limiter != null && limiter.getPageSize() > 0) {
            StringBuilder builder = new StringBuilder();
            builder
                    .append(basicQuery)
                    .append(" ")
                    .append(Query.getPostgresQuery(limiter));
            return builder.toString();
        }

        return basicQuery;
    }

    public static String modifyToCountQuery(String theQuery) {
        StringBuilder builder = new StringBuilder();
        return builder
                .append("SELECT COUNT(*) FROM (")
                .append(theQuery)
                .append(") counted")
                .toString();
    }
}
