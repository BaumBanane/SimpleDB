package simpledb.materialize;

import java.util.*;

import simpledb.tx.Transaction;
import simpledb.record.*;
import simpledb.plan.Plan;
import simpledb.query.*;

/**
 * The Plan class for the <i>sort</i> operator.
 *
 * @author Edward Sciore
 */
public class SortPlan implements Plan {
    private final Transaction tx;
    private final Plan p;
    private final Schema sch;
    private final RecordComparator comp;
    private final List<SortField> fields;

    /**
     * Create a sort plan for the specified query.
     *
     * @param p          the plan for the underlying query
     * @param sortfields the fields to sort by
     * @param tx         the calling transaction
     */
    public SortPlan(Transaction tx, Plan p, List<SortField> sortfields) {
        this.tx = tx;
        this.p = p;
        sch = p.schema();
        comp = new RecordComparator(sortfields);
        this.fields = sortfields;
    }

    /**
     * This method is where most of the action is.
     * Up to 2 sorted temporary tables are created,
     * and are passed into SortScan for final merging.
     *
     * @see simpledb.plan.Plan#open()
     */
    public Scan open() {
        Scan src = p.open();
        List<TempTable> runs = splitIntoRuns(src);
        src.close();
        int k = mergeFactor(runs.size());
        while (runs.size() > k)
            runs = doAMergeIteration(runs, k);
        return new SortScan(runs, comp);
    }

    /**
     * Return the number of runs to merge at a time.
     * Merging k runs needs one buffer for each run and one for the
     * output, so the number of available buffers limits k. Fewer runs
     * than that are merged all at once.
     *
     * @param numruns the number of runs to be merged
     * @return the number of runs to merge at a time, at least 2
     */
    private int mergeFactor(int numruns) {
        int k = tx.availableBuffs() - 1;  // one buffer for the output run
        if (k < 2)
            k = 2;                        // a merge needs at least 2 runs
        return Math.min(k, numruns);
    }


    /**
     * Return the maximum number of runs that can be merged at a time.
     *
     * @param tx the transaction that has the buffers
     * @return the number of runs to merge at a time, at least 2
     */
    public static int maxMergeFactor(Transaction tx) {
        int k = tx.availableBuffs() - 1;
        return Math.max(2, k);
    }

    /**
     * Return the number of blocks in the sorted table,
     * which is the same as it would be in a
     * materialized table.
     * It does <i>not</i> include the one-time cost
     * of materializing and sorting the records.
     *
     * @see simpledb.plan.Plan#blocksAccessed()
     */
    public int blocksAccessed() {
        // does not include the one-time cost of sorting
        Plan mp = new MaterializePlan(tx, p); // not opened; just for analysis
        return mp.blocksAccessed();
    }

    /**
     * Return the number of records in the sorted table,
     * which is the same as in the underlying query.
     *
     * @see simpledb.plan.Plan#recordsOutput()
     */
    public int recordsOutput() {
        return p.recordsOutput();
    }

    /**
     * Return the number of distinct field values in
     * the sorted table, which is the same as in
     * the underlying query.
     *
     * @see simpledb.plan.Plan#distinctValues(java.lang.String)
     */
    public int distinctValues(String fldname) {
        return p.distinctValues(fldname);
    }

    /**
     * Return the schema of the sorted table, which
     * is the same as in the underlying query.
     *
     * @see simpledb.plan.Plan#schema()
     */
    public Schema schema() {
        return sch;
    }

    private List<TempTable> splitIntoRuns(Scan src) {
        List<TempTable> temps = new ArrayList<>();
        src.beforeFirst();
        if (!src.next())
            return temps;
        TempTable currenttemp = new TempTable(tx, sch);
        temps.add(currenttemp);
        UpdateScan currentscan = currenttemp.open();
        while (copy(src, currentscan))
            if (comp.compare(src, currentscan) < 0) {
                // start a new run
                currentscan.close();
                currenttemp = new TempTable(tx, sch);
                temps.add(currenttemp);
                currentscan = currenttemp.open();
            }
        currentscan.close();
        return temps;
    }

    private List<TempTable> doAMergeIteration(List<TempTable> runs, int k) {
        List<TempTable> result = new ArrayList<>();
        while (runs.size() > 1) {
            List<TempTable> group = new ArrayList<>();
            while (!runs.isEmpty() && group.size() < k)
                group.add(runs.remove(0));
            result.add(mergeRuns(group));
        }
        if (runs.size() == 1)
            result.add(runs.get(0));
        return result;
    }

    /**
     * Merge the specified runs into a single run.
     * The method repeatedly copies the smallest of the current records
     * of the runs, which is the same merge that SortScan performs,
     * except that the output is written to a temporary table.
     *
     * @param runs the runs to merge
     * @return the merged run
     */
    private TempTable mergeRuns(List<TempTable> runs) {
        List<Scan> srcs = new ArrayList<>();
        boolean[] hasmore = new boolean[runs.size()];
        for (int i = 0; i < runs.size(); i++) {
            Scan src = runs.get(i).open();
            srcs.add(src);
            hasmore[i] = src.next();
        }

        TempTable result = new TempTable(tx, sch);
        UpdateScan dest = result.open();

        while (true) {
            int lowest = -1;
            for (int i = 0; i < srcs.size(); i++) {
                if (!hasmore[i])
                    continue;
                if (lowest < 0 || comp.compare(srcs.get(i), srcs.get(lowest)) < 0)
                    lowest = i;
            }
            if (lowest < 0)
                break;
            hasmore[lowest] = copy(srcs.get(lowest), dest);
        }

        for (Scan src : srcs)
            src.close();
        dest.close();
        return result;
    }

    private boolean copy(Scan src, UpdateScan dest) {
        dest.insert();
        for (String fldname : sch.fields())
            dest.setVal(fldname, src.getVal(fldname));
        return src.next();
    }

    public boolean sortsOn(SortField fld) {
        return !fields.isEmpty()
                && fields.get(0).field().equals(fld.field())
                && fields.get(0).isAscending() == fld.isAscending();
    }

    /**
     * Returns a description of this plan, followed by the description
     * of its underlying plan at the next level of indentation.
     *
     * @see simpledb.plan.Plan#planString(int)
     */
    public String planString(int depth) {
        return Plan.indent(depth) + "SortPlan " + fields
                + "  (B=" + blocksAccessed() + ", R=" + recordsOutput() + ")\n"
                + p.planString(depth + 1);
    }
}
