package simpledb.opt;

import java.util.*;

import simpledb.materialize.*;
import simpledb.metadata.MetadataMgr;
import simpledb.query.SortField;
import simpledb.tx.Transaction;
import simpledb.parse.QueryData;
import simpledb.plan.*;

/**
 * A query planner that optimizes using a heuristic-based algorithm.
 *
 * @author Edward Sciore
 */
public class HeuristicQueryPlanner implements QueryPlanner {
    private final MetadataMgr mdm;

    public HeuristicQueryPlanner(MetadataMgr mdm) {
        this.mdm = mdm;
    }

    /**
     * Creates an optimized left-deep query plan using the following
     * heuristics.
     * H1. Choose the smallest table (considering selection predicates)
     * to be first in the join order.
     * H2. Add the table to the join order which
     * results in the smallest output.
     */
    public Plan createPlan(QueryData data, Transaction tx) {
        Collection<TablePlanner> tableplanners = new ArrayList<>();

        // Step 1:  Create a TablePlanner object for each mentioned table
        for (String tblname : data.tables()) {
            TablePlanner tp = new TablePlanner(tblname, data.pred(), tx, mdm);
            tableplanners.add(tp);
        }

        // Step 2:  Choose the lowest-size plan to begin the join order
        Plan currentplan = getLowestSelectPlan(tableplanners);

        // Step 3:  Repeatedly add a plan to the join order
        while (!tableplanners.isEmpty()) {
            // prefer merge join if query is not distinct, not grouped, not only one aggregate, and merge join sorts on sort records
            boolean sortWouldBeUseful = !data.isDistinct() && data.groupFields().isEmpty() && data.aggregates().isEmpty() && tableplanners.size() == 1;
            Plan p = getLowestJoinPlan(currentplan, tableplanners, sortWouldBeUseful ? data.sortFields() : Collections.emptyList());
            if (p != null)
                currentplan = p;
            else  // no applicable join
                currentplan = getLowestProductPlan(currentplan, tableplanners);
        }

        // Step 4: Group and aggregate if "group by" clause or aggregates are present
        if (!data.groupFields().isEmpty() || !data.aggregates().isEmpty()) {
            currentplan = new GroupByPlan(tx, currentplan, data.groupFields(), data.aggregates());
        }

        // Step 5: Sort fields if "order by" clause is present.
        // When "distinct" is also present the sort is done while the
        // duplicates are being removed, because DistinctPlan sorts anyway,
        // so it might as well sort on sortFields.
        // Also check if the plan is already sorted, and only sort if it is not.
        boolean alreadySorted = data.sortFields().size() == 1 && currentplan.sortsOn(data.sortFields().get(0));
        if (!data.sortFields().isEmpty() && !alreadySorted && !data.isDistinct()) {
            currentplan = new SortPlan(tx, currentplan, data.sortFields());
        }

        // Step 6: Remove duplicate records if "distinct" is present.
        // This is done before the projection, considering only
        // the fields the projection will be on, because this allows
        // to integrate the sorting into the duplicate elimination.
        // Projection preserves order.
        // This assumption breaks when selecting distinct colA
        // with order by colB, which is therefore
        // prevented by the validator.
        if (data.isDistinct()) {
            currentplan = new DistinctPlan(tx, currentplan, data.fields(), data.sortFields());
        }
        // Step 7:  Project on the field names
        currentplan = new ProjectPlan(currentplan, data.fields());


        return currentplan;
    }

    private Plan getLowestSelectPlan(Collection<TablePlanner> tableplanners) {
        TablePlanner besttp = null;
        Plan bestplan = null;
        for (TablePlanner tp : tableplanners) {
            Plan plan = tp.makeSelectPlan();
            if (bestplan == null || plan.recordsOutput() < bestplan.recordsOutput()) {
                besttp = tp;
                bestplan = plan;
            }
        }
        tableplanners.remove(besttp);
        return bestplan;
    }

    private Plan getLowestJoinPlan(Plan current, Collection<TablePlanner> tableplanners, List<SortField> outputSortRequirement) {
        TablePlanner besttp = null;
        Plan bestplan = null;
        for (TablePlanner tp : tableplanners) {
            Plan plan = tp.makeJoinPlan(current, outputSortRequirement);
            if (plan != null && (bestplan == null || plan.recordsOutput() < bestplan.recordsOutput())) {
                besttp = tp;
                bestplan = plan;
            }
        }
        if (bestplan != null)
            tableplanners.remove(besttp);
        return bestplan;
    }

    private Plan getLowestProductPlan(Plan current, Collection<TablePlanner> tableplanners) {
        TablePlanner besttp = null;
        Plan bestplan = null;
        for (TablePlanner tp : tableplanners) {
            Plan plan = tp.makeProductPlan(current);
            if (bestplan == null || plan.recordsOutput() < bestplan.recordsOutput()) {
                besttp = tp;
                bestplan = plan;
            }
        }
        tableplanners.remove(besttp);
        return bestplan;
    }

    public void setPlanner(Planner p) {
        // for use in planning views, which
        // for simplicity this code doesn't do.
    }
}
