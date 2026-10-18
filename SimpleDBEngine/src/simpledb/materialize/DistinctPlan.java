package simpledb.materialize;

import simpledb.plan.Plan;
import simpledb.query.Scan;
import simpledb.query.SortField;
import simpledb.record.Schema;
import simpledb.tx.Transaction;

import java.util.*;

/**
 * The Plan class for the <i>distinct</i> operator.
 * The underlying plan is sorted on the output fields so that
 * duplicate records end up next to each other and can be
 * removed in a single pass by DistinctScan.
 */
public class DistinctPlan implements Plan {
    private final Plan p;
    private final List<SortField> fields;
    private final Schema sch;

    /**
     * Create a distinct plan for the underlying query.
     *
     * @param tx               the calling transaction
     * @param p                the plan for the underlying query
     * @param fldnames         the fields that duplicates are judged on
     * @param sortRequirements the fields that the output should be sorted on
     */
    public DistinctPlan(Transaction tx, Plan p, List<String> fldnames, List<SortField> sortRequirements) {
        List<SortField> sortfields = new ArrayList<>();
        if (sortRequirements != null) {
            sortfields.addAll(sortRequirements);
        }
        for (String fldname : fldnames) {
            boolean alreadySorting = false;
            if (sortRequirements != null) {
                for (SortField requirement : sortRequirements) {
                    if (requirement.field().equals(fldname)) {
                        alreadySorting = true;
                        break;
                    }
                }
            }
            if (!alreadySorting) {
                sortfields.add(new SortField(fldname));
            }
        }

        this.p = new SortPlan(tx, p, sortfields);
        this.fields = sortfields;
        this.sch = p.schema();
    }

    /**
     * Open a scan on the sorted underlying plan and
     * wrap it in a scan that skips duplicate records.
     *
     * @see simpledb.plan.Plan#open()
     */
    public Scan open() {
        Scan s = p.open();
        return new DistinctScan(s, fields);
    }

    /**
     * Return the number of blocks in the sorted table.
     * It does not include the one-time cost of sorting.
     *
     * @see simpledb.plan.Plan#blocksAccessed()
     */
    public int blocksAccessed() {
        return p.blocksAccessed();
    }

    /**
     * Return the estimated number of distinct records.
     * Assuming the fields are independent, this is the product of
     * the number of distinct values of each field, but it can never
     * be more than the number of records in the underlying query.
     *
     * @see simpledb.plan.Plan#recordsOutput()
     */
    public int recordsOutput() {
        int numvals = 1;
        for (SortField fld : fields)
            numvals *= p.distinctValues(fld.field());
        return Math.min(numvals, p.recordsOutput());
    }

    /**
     * Return the number of distinct field values,
     * which is the same as in the underlying query.
     *
     * @see simpledb.plan.Plan#distinctValues(java.lang.String)
     */
    public int distinctValues(String fldname) {
        return p.distinctValues(fldname);
    }

    /**
     * Return the schema of the output, which is the same as
     * in the underlying query. Distinct removes records, not fields.
     *
     * @see simpledb.plan.Plan#schema()
     */
    public Schema schema() {
        return sch;
    }

    /**
     * Returns whether the output table is sorted.
     * If this is done by sorting, the output is sorted.
     *
     * @see simpledb.plan.Plan#sortsOn(simpledb.query.SortField)
     */
    public boolean sortsOn(SortField fld) {
        return !fields.isEmpty()
                && fields.get(0).isAscending() == fld.isAscending()
                && fields.get(0).field().equals(fld.field());
    }

    /**
     * Returns a description of this plan, followed by the description
     * of its underlying plan at the next level of indentation.
     *
     * @see simpledb.plan.Plan#planString(int)
     */
    public String planString(int depth) {
        List<String> names = new ArrayList<>();
        for (SortField fld : fields)
            names.add(fld.field());
        return Plan.indent(depth) + "DistinctPlan " + names
                + "  (B=" + blocksAccessed() + ", R=" + recordsOutput() + ")\n"
                + p.planString(depth + 1);
    }
}
