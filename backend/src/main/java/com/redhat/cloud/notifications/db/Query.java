package com.redhat.cloud.notifications.db;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.QueryParam;
import java.util.function.Function;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.CASE_INSENSITIVE;

@Schema(description = "Query data for paging and sorting")
public class Query {

    private static final Pattern SORT_FIELD_PATTERN = Pattern.compile("^[a-z0-9._]+$", CASE_INSENSITIVE);
    private static final int DEFAULT_RESULTS_PER_PAGE  = 20;

    @Parameter(description = "Number of items per page. If the value is 0, it will return all elements.",
               schema = @Schema(type = SchemaType.INTEGER))
    @QueryParam("limit")
    @DefaultValue(DEFAULT_RESULTS_PER_PAGE + "")
    private Integer pageSize;

    @Parameter(description = "Page number. Starts at first page (0), if not specified starts at first page.", schema = @Schema(type = SchemaType.INTEGER))
    @QueryParam("pageNumber")
    private Integer pageNumber;

    @Parameter(description = "Number of the starting page.")
    @QueryParam("offset")
    private Integer offset;

    @Parameter(description = "Sort by expression in the form 'name:{asc|desc}'")
    @QueryParam("sort_by")
    private String sortBy;

    public static class Limit {
        private final int limit;
        private final int offset;

        public Limit(int limit, int offset) {
            this.limit = limit;
            this.offset = offset;
        }

        public int getLimit() {
            return limit;
        }

        public int getOffset() {
            return offset;
        }

        public static int calculateOffset(int pageNumber, int pageSize) {
            return pageNumber * pageSize;
        }
    }

    public Limit getLimit() {
        if (pageSize == null) {
            pageSize = DEFAULT_RESULTS_PER_PAGE;
        }

        // offset takes precedence over pageNumber if both are set
        if (pageNumber != null && offset == null) {
            offset = Limit.calculateOffset(pageNumber, pageSize);
        }
        if (offset == null) {
            offset = 0;
        }
        return new Limit(pageSize, offset);
    }

    public static class Sort {
        private String sortColumn;

        public enum Order {
            ASC, DESC
        }

        private Order sortOrder;

        public Sort(String sortColumn) {
            this(sortColumn, Order.ASC);
        }

        public Sort(String sortColumn, Order sortOrder) {
            this.sortColumn = sortColumn;
            this.sortOrder = sortOrder;
        }

        public String getSortColumn() {
            return sortColumn;
        }

        public void setSortColumn(String sortColumn) {
            this.sortColumn = sortColumn;
        }

        public Order getSortOrder() {
            return sortOrder;
        }

        public void setSortOrder(Order sortOrder) {
            this.sortOrder = sortOrder;
        }

        public String getSortQuery() {
            return "ORDER BY " + this.getSortColumn() + " " + this.getSortOrder().toString();
        }
    }

    public Sort getSort() {
        // Endpoints: sort by: name, type, "last connection status" (?), enabled
        //      -> endpoint_id, name, endpoint_type, enabled are the accepted parameter names
        // TODO Should they be id, name, type, enabled for consistency and then modified in the actual query to Postgres?
        // And if it's not an accepted value? Throw exception?

        if (sortBy == null || sortBy.length() < 1) {
            return null;
        }

        String[] sortSplit = sortBy.split(":");
        if (!SORT_FIELD_PATTERN.matcher(sortSplit[0]).matches()) {
            throw new BadRequestException("Invalid 'sort_by' query parameter");
        } else {
            Sort sort = new Sort(sortSplit[0]);
            if (sortSplit.length > 1) {
                try {
                    Sort.Order order = Sort.Order.valueOf(sortSplit[1].toUpperCase());
                    sort.setSortOrder(order);
                } catch (IllegalArgumentException | NullPointerException iae) {
                }
            }
            return sort;
        }
    }

    public String getModifiedQuery(String basicQuery) {
        // Use the internal Query
        // What's the proper order? SORT first, then LIMIT? COUNT as last one?
        String query = basicQuery;
        Sort sort = getSort();
        if (sort != null) {
            query = modifyWithSort(query, sort);
        }
        return query;
    }

    public static Function<String, String> modifyToCountQuery() {
        return s -> "SELECT COUNT(*) FROM (" +
                s +
                ") counted";
    }

    public static String modifyToCountQuery(String theQuery) {
        return "SELECT COUNT(*) FROM (" +
                theQuery +
                ") counted";
    }

    private static String modifyWithSort(String theQuery, Query.Sort sorter) {
        return theQuery +
                " ORDER BY " +
                sorter.getSortColumn() + " " +
                sorter.getSortOrder().toString();
    }
}
