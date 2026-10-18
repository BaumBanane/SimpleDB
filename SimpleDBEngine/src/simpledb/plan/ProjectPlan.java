package simpledb.plan;

import java.util.List;

import simpledb.record.Schema;
import simpledb.query.*;

/**
 * The Plan class corresponding to the <i>project</i>
 * relational algebra operator.
 *
 * @author Edward Sciore
 */
public class ProjectPlan implements Plan {
    private final Plan p;
    private final Schema schema = new Schema();

    /**
     * Creates a new project node in the query tree,
     * having the specified subquery and field list.
     *
     * @param p         the subquery
     * @param fieldlist the list of fields
     */
    public ProjectPlan(Plan p, List<String> fieldlist) {
        this.p = p;
        for (String fldname : fieldlist)
            schema.add(fldname, p.schema());
    }

    /**
     * Creates a project scan for this query.
     *
     * @see simpledb.plan.Plan#open()
     */
    public Scan open() {
        Scan s = p.open();
        return new ProjectScan(s, schema.fields());
    }

    /**
     * Estimates the number of block accesses in the projection,
     * which is the same as in the underlying query.
     *
     * @see simpledb.plan.Plan#blocksAccessed()
     */
    public int blocksAccessed() {
        return p.blocksAccessed();
    }

    /**
     * Estimates the number of output records in the projection,
     * which is the same as in the underlying query.
     *
     * @see simpledb.plan.Plan#recordsOutput()
     */
    public int recordsOutput() {
        return p.recordsOutput();
    }

    /**
     * Estimates the number of distinct field values
     * in the projection,
     * which is the same as in the underlying query.
     *
     * @see simpledb.plan.Plan#distinctValues(java.lang.String)
     */
    public int distinctValues(String fldname) {
        return p.distinctValues(fldname);
    }

    /**
     * Returns the schema of the projection,
     * which is taken from the field list.
     *
     * @see simpledb.plan.Plan#schema()
     */
    public Schema schema() {
        return schema;
    }

    /**
     * Returns whether the selection is sorted,
     * which is the same as in the underlying query
     * if the sort field is in the output.
     *
     * @see simpledb.plan.Plan#sortsOn(simpledb.query.SortField)
     */
    public boolean sortsOn(SortField fld) {
        return schema.hasField(fld.field()) && p.sortsOn(fld);
    }

    /**
     * Returns a description of this plan, followed by the description
     * of its underlying plan at the next level of indentation.
     *
     * @see simpledb.plan.Plan#planString(int)
     */
    public String planString(int depth) {
        return Plan.indent(depth) + "ProjectPlan " + schema.fields()
                + "  (B=" + blocksAccessed() + ", R=" + recordsOutput() + ")\n"
                + p.planString(depth + 1);
    }
}
