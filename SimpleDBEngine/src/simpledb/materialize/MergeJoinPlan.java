package simpledb.materialize;

import simpledb.tx.Transaction;
import simpledb.plan.Plan;
import simpledb.query.*;
import simpledb.record.*;

import java.util.*;

/**
 * The Plan class for the <i>mergejoin</i> operator.
 *
 * @author Edward Sciore
 */
public class MergeJoinPlan implements Plan {
    private final Plan p1;
    private final Plan p2;
    private final SortField fld1;
    private final SortField fld2;
    private final Schema sch = new Schema();
    private final boolean sortP1;
    private final Transaction tx;

    /**
     * Creates a mergejoin plan for the two specified queries.
     * The RHS must be materialized after it is sorted,
     * in order to deal with possible duplicates.
     *
     * @param p1       the LHS query plan
     * @param p2       the RHS query plan
     * @param fldname1 the LHS join field
     * @param fldname2 the RHS join field
     * @param tx       the calling transaction
     */
    public MergeJoinPlan(Transaction tx, Plan p1, Plan p2, String fldname1, String fldname2) {
        // only sort p1 if it is not already sorted
        sortP1 = !p1.sortsOn(new SortField(fldname1, true));
        fld1 = new SortField(fldname1, true);
        if (sortP1) {
            List<SortField> sortlist1 = List.of(fld1);
            this.p1 = new SortPlan(tx, p1, sortlist1);
        } else {
            this.p1 = p1;
        }

        // always sort p2 because MergeJoinScan needs SortScan.savePosition() and SortScan.restorePosition()
        fld2 = new SortField(fldname2, true);
        List<SortField> sortlist2 = List.of(fld2);
        this.p2 = new SortPlan(tx, p2, sortlist2);

        sch.addAll(p1.schema());
        sch.addAll(p2.schema());
        this.tx = tx;
    }

    /**
     * The method first sorts its two underlying scans
     * on their join field. It then returns a mergejoin scan
     * of the two sorted table scans.
     *
     * @see Plan#open()
     */
    public Scan open() {
        Scan s1 = p1.open();
        SortScan s2 = (SortScan) p2.open();
        return new MergeJoinScan(s1, s2, fld1.field(), fld2.field());
    }

    /**
     * Return the number of block acceses required to
     * mergejoin the sorted tables.
     * Since a mergejoin can be preformed with a single
     * pass through each table, the method returns
     * the sum of the block accesses of the
     * materialized sorted tables.
     * Since SortPlan.blocksAccessed() does not consider it,
     * this method also considers the sort cost.
     * SortPlan.blocksAccessed() does not consider it
     * since otherwise NestedLoopJoinPlan would assume
     * that it has to sort the inner relation for every outer block
     * if it is handed a SortPlan.
     *
     * @see Plan#blocksAccessed()
     */
    public int blocksAccessed() {
        int blocks1 = p1.blocksAccessed();
        int blocks2 = p2.blocksAccessed();
        int mergeCost = blocks2 + blocks1;
        int sortCost = 0;
        int mergeFactor = SortPlan.maxMergeFactor(tx);
        double logK = Math.log(mergeFactor);

        // SortPlan.java now also supports multiway sort, so divide by log(mergeFactor)!
        // SimpleDB also splits into runs without sorting them, but then the last pass is skipped
        if (sortP1) {
            // since SimpleDB creates a run whenever a tuple is smaller than the one before,
            // the average run size will be 2.
            // The number of runs will thus be number of records divided by 2
            int records1 = p1.recordsOutput();
            int expectedRuns1 = Math.max(1, records1 / 2);
            // outer Math.max: SimpleDB splits into runs even if only one block
            int passes1 = (int) Math.max(1, Math.ceil(Math.log(expectedRuns1) / logK));
            sortCost += 2 * blocks1 * passes1;
        }
        int records2 = p2.recordsOutput();
        int expectedRuns2 = Math.max(1, records2 / 2);
        int passes2 = (int) Math.max(1, Math.ceil(Math.log(expectedRuns2) / logK));
        sortCost += 2 * blocks2 * passes2;

        return sortCost + mergeCost;
    }

    /**
     * Return the number of records in the join.
     * Assuming uniform distribution, the formula is:
     * <pre> R(join(p1,p2)) = R(p1)*R(p2)/max{V(p1,F1),V(p2,F2)}</pre>
     *
     * @see Plan#recordsOutput()
     */
    public int recordsOutput() {
        int maxvals = Math.max(p1.distinctValues(fld1.field()),
                p2.distinctValues(fld2.field()));
        return (p1.recordsOutput() * p2.recordsOutput()) / maxvals;
    }

    /**
     * Estimate the distinct number of field values in the join.
     * Since the join does not increase or decrease field values,
     * the estimate is the same as in the appropriate underlying query.
     *
     * @see Plan#distinctValues(String)
     */
    public int distinctValues(String fldname) {
        if (p1.schema().hasField(fldname))
            return p1.distinctValues(fldname);
        else
            return p2.distinctValues(fldname);
    }

    /**
     * Return the schema of the join,
     * which is the union of the schemas of the underlying queries.
     *
     * @see Plan#schema()
     */
    public Schema schema() {
        return sch;
    }

    /**
     * Return whether the join is sorted,
     * which is true for the join field of both relations.
     *
     * @see Plan#sortsOn(SortField)
     */
    public boolean sortsOn(SortField fld) {
        return fld.field().equals(fld1.field()) && fld.isAscending() == fld1.isAscending()
                || fld.field().equals(fld2.field()) && fld.isAscending() == fld2.isAscending();
    }

    /**
     * Returns a description of this plan, followed by the descriptions
     * of both underlying plans at the next level of indentation.
     *
     * @see Plan#planString(int)
     */
    public String planString(int depth) {
        return Plan.indent(depth) + "MergeJoinPlan [" + fld1.field() + "=" + fld2.field() + "]"
                + "  (B=" + blocksAccessed() + ", R=" + recordsOutput() + ")\n"
                + p1.planString(depth + 1)
                + p2.planString(depth + 1);
    }
}

