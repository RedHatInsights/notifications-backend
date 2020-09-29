package com.redhat.cloud.notifications.db;

public class Query {
    public static class Limit {
        private int pageNumber;
        private int pageSize;

        public Limit(int pageNumber, int pageSize) {
            this.pageNumber = pageNumber;
            this.pageSize = pageSize;
        }

        public int getPageSize() {
            return pageSize;
        }

        public int calculateOffset() {
            return pageNumber * pageSize;
        }
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
}
