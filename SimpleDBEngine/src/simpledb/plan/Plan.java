package simpledb.plan;

import simpledb.query.Scan;
import simpledb.query.SortField;
import simpledb.record.*;

/**
 * The interface implemented by each query plan.
 * There is a Plan class for each relational algebra operator.
 *
 * @author Edward Sciore
 *
 */
public interface Plan {

    /**
     * Opens a scan corresponding to this plan.
     * The scan will be positioned before its first record.
     *
     * @return a scan
     */
    Scan open();

    /**
     * Returns an estimate of the number of block accesses
     * that will occur when the scan is read to completion.
     *
     * @return the estimated number of block accesses
     */
    int blocksAccessed();

    /**
     * Returns an estimate of the number of records
     * in the query's output table.
     *
     * @return the estimated number of output records
     */
    int recordsOutput();

    /**
     * Returns an estimate of the number of distinct values
     * for the specified field in the query's output table.
     *
     * @param fldname the name of a field
     * @return the estimated number of distinct field values in the output
     */
    int distinctValues(String fldname);

    /**
     * Returns the schema of the query.
     *
     * @return the query's schema
     */
    Schema schema();

    /**
     * Returns true if the plan guarantees that its output records
     * will be sorted by the specified field.
     *
     * @param fld the field
     * @return true if the output is sorted by the field
     */
    default boolean sortsOn(SortField fld) {
        return false;
    }

    /**
     * Returns a description of this plan and of the plans below it,
     * one plan per line, each level indented three spaces further than
     * the level above it. A plan appends the description of its
     * underlying plans, so calling this method on the top plan of a
     * query returns the whole query plan.
     * The default implementation describes the plan by its class name
     * and its cost estimates. A plan that has underlying plans should
     * override it so that those plans are shown as well.
     *
     * @param depth the number of levels this plan is below the top plan
     * @return a description of this plan and the plans below it
     */
    default String planString(int depth) {
        return indent(depth) + getClass().getSimpleName()
                + "  (B=" + blocksAccessed() + ", R=" + recordsOutput() + ")\n";
    }

    /**
     * Returns the indentation that planString uses at the given depth.
     *
     * @param depth the number of levels below the top plan
     * @return a string of three spaces per level
     */
    static String indent(int depth) {
        return "   ".repeat(Math.max(0, depth));
    }
}
