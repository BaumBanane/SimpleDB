package simpledb.parse;

import java.util.*;

import simpledb.materialize.AggregationFn;
import simpledb.query.*;

/**
 * Data for the SQL <i>select</i> statement.
 *
 * @author Edward Sciore
 */
public class QueryData {
    private final List<String> fields;
    private final Collection<String> tables;
    private final Predicate pred;
    private final List<SortField> groupFields;
    private final List<AggregationFn> aggregates;
    private final List<SortField> sortFields;
    private final boolean isDistinct;

    /**
     * Saves the field, table list, predicate, group by fields,
     * aggregates and sort fields.
     */
    public QueryData(List<String> fields, Collection<String> tables, Predicate pred,
                     List<SortField> groupFields, List<AggregationFn> aggregates,
                     List<SortField> sortFields, boolean isDistinct) {
        this.fields = fields;
        this.tables = tables;
        this.pred = pred;
        this.groupFields = groupFields;
        this.aggregates = aggregates;
        this.sortFields = sortFields;
        this.isDistinct = isDistinct;
    }

    /**
     * Returns the fields mentioned in the select clause.
     * Aggregates are represented by their output field names.
     *
     * @return a list of field names
     */
    public List<String> fields() {
        return fields;
    }

    /**
     * Returns the tables mentioned in the from clause.
     *
     * @return a collection of table names
     */
    public Collection<String> tables() {
        return tables;
    }

    /**
     * Returns the predicate that describes which
     * records should be in the output table.
     *
     * @return the query predicate
     */
    public Predicate pred() {
        return pred;
    }

    /**
     * Returns the fields mentioned in the group by clause.
     *
     * @return a list of {@code SortField} objects
     */
    public List<SortField> groupFields() {
        return groupFields;
    }

    /**
     * Returns the aggregation functions mentioned in the select clause.
     *
     * @return a list of aggregation functions
     */
    public List<AggregationFn> aggregates() {
        return aggregates;
    }

    /**
     * Returns the fields mentioned in the order by clause
     * for which the query would be sorted by.
     *
     * @return a list of {@code SortField} objects
     */
    public List<SortField> sortFields() {
        return sortFields;
    }

    /**
     * returns the isDistinct flag as a boolean.
     */
    public boolean isDistinct() {
        return this.isDistinct;
    }

    public String toString() {
        List<String> selects = new ArrayList<>();
        for (String fldname : fields)
            selects.add(displayName(fldname));
        String result = "select " + (isDistinct ? "distinct " : "") + String.join(", ", selects);
        result += " from " + String.join(", ", tables);
        String predstring = pred.toString();
        if (!predstring.equals(""))
            result += " where " + predstring;
        if (!groupFields.isEmpty()) {
            List<String> groups = new ArrayList<>();
            for (SortField fld : groupFields)
                groups.add(fld.field());
            result += " group by " + String.join(", ", groups);
        }
        if (!sortFields.isEmpty()) {
            List<String> sorts = new ArrayList<>();
            for (SortField fld : sortFields)
                sorts.add(displayName(fld.field()) + (fld.isAscending() ? " asc" : " desc"));
            result += " order by " + String.join(", ", sorts);
        }
        return result;
    }

    /**
     * Returns the SQL form of a field name, so that an
     * aggregate field such as countofsid is shown as count(sid).
     */
    private String displayName(String fldname) {
        for (AggregationFn fn : aggregates)
            if (fn.fieldName().equals(fldname))
                return fn.toString();
        return fldname;
    }
}