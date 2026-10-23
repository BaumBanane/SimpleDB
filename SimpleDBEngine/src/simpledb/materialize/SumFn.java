package simpledb.materialize;

import simpledb.query.*;

/**
 * The <i>sum</i> aggregation function.
 * The aggregated field must be an integer field.
 */
public class SumFn implements AggregationFn {
    private final String fldname;
    private int sum;

    /**
     * Create a sum aggregation function for the specified field.
     *
     * @param fldname the name of the aggregated field
     */
    public SumFn(String fldname) {
        this.fldname = fldname;
    }

    /**
     * Start a new sum with the field value in the current record.
     *
     * @see simpledb.materialize.AggregationFn#processFirst(simpledb.query.Scan)
     */
    public void processFirst(Scan s) {
        sum = s.getInt(fldname);
    }

    /**
     * Add the field value in the current record to the sum.
     *
     * @see simpledb.materialize.AggregationFn#processNext(simpledb.query.Scan)
     */
    public void processNext(Scan s) {
        sum += s.getInt(fldname);
    }

    /**
     * Return the field's name, prepended by "sumof".
     *
     * @see simpledb.materialize.AggregationFn#fieldName()
     */
    public String fieldName() {
        return "sumof" + fldname;
    }

    /**
     * Return the field's name.
     *
     * @see simpledb.materialize.AggregationFn#fieldName()
     */
    public String fieldArgument() {
        return fldname;
    }

    /**
     * Return the current sum.
     *
     * @see simpledb.materialize.AggregationFn#value()
     */
    public Constant value() {
        return new Constant(sum);
    }

    public String toString() {
        return "sum(" + fldname + ")";
    }
}
