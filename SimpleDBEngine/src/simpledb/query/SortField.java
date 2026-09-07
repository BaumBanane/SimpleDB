package simpledb.query;

/**
 * The SortField class storing the field to be sorted by
 * and its corresponding direction.
 */
public class SortField {
    private String field;
    private boolean ascending;

    /**
     * Create a sort field with default ascending ordering.
     * @param field the field name
     */
    public SortField(String field) {
        this.field = field;
        this.ascending = true;
    }

    /**
     * Create a sort field with its provided ordering.
     * @param field the field name
     * @param ascending whether the sorting should be in ascending order
     */
    public SortField(String field, boolean ascending) {
        this.field = field;
        this.ascending = ascending;
    }

    public String field() {
        return field;
    }

    public boolean isAscending() {
        return ascending;
    }

    public String toString() {
        return field + (ascending ? " asc" : " desc");
    }
}
