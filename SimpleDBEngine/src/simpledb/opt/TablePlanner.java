package simpledb.opt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import simpledb.tx.Transaction;
import simpledb.record.*;
import simpledb.query.*;
import simpledb.metadata.*;
import simpledb.index.planner.*;
import simpledb.materialize.HashJoinPlan;
import simpledb.materialize.MergeJoinPlan;
import simpledb.multibuffer.MultibufferProductPlan;
import simpledb.plan.*;

/**
 * This class contains methods for planning a single table.
 *
 * @author Edward Sciore
 */
class TablePlanner {
    private final TablePlan myplan;
    private final Predicate mypred;
    private final Schema myschema;
    private final Map<String, IndexInfo> indexes;
    private final Transaction tx;

    /**
     * Creates a new table planner.
     * The specified predicate applies to the entire query.
     * The table planner is responsible for determining
     * which portion of the predicate is useful to the table,
     * and when indexes are useful.
     *
     * @param tblname the name of the table
     * @param mypred  the query predicate
     * @param tx      the calling transaction
     */
    public TablePlanner(String tblname, Predicate mypred, Transaction tx, MetadataMgr mdm) {
        this.mypred = mypred;
        this.tx = tx;
        myplan = new TablePlan(tx, tblname, mdm);
        myschema = myplan.schema();
        indexes = mdm.getIndexInfo(tblname, tx);
    }

    /**
     * Constructs a select plan for the table.
     * The plan will use an indexselect, if possible.
     *
     * @return a select plan for the table.
     */
    public Plan makeSelectPlan() {
        Plan p = makeIndexSelect();
        if (p == null)
            p = myplan;
        return addSelectPred(p);
    }

    /**
     * Constructs a join plan of the specified plan
     * and the table.  The plan will use an indexjoin, if possible.
     * (Which means that if an indexselect is also possible,
     * the indexjoin operator takes precedence.)
     * The method returns null if no join is possible.
     * It always makes a left-deep tree.
     * Current is always left (outer),
     * this table is always right (inner).
     *
     * @param current the specified plan
     * @return a join plan of the plan and this table
     */
    public Plan makeJoinPlan(Plan current, List<SortField> outputSortRequirement) {
        Schema currsch = current.schema();
        Predicate joinpred = mypred.joinSubPred(myschema, currsch);
        if (joinpred == null)
            return null;
        List<Plan> possiblePlans = new ArrayList<>();
        Plan indexPlan = makeIndexJoin(current, currsch);
        if (indexPlan != null) return indexPlan; // always prefer index join if possible
        Plan mergePlan = makeMergeJoin(current, currsch);
        if (mergePlan != null) {
            boolean isSingleSort = outputSortRequirement != null && outputSortRequirement.size() == 1;
            boolean satisfiesSort = isSingleSort && mergePlan.sortsOn(outputSortRequirement.get(0));
            if (satisfiesSort)
                return mergePlan;
            else
                possiblePlans.add(mergePlan);
        }
        Plan hashPlan = makeHashJoin(current, currsch);
        if (hashPlan != null) possiblePlans.add(hashPlan);
        Plan nestedLoopsPlan = makeNestedLoopsJoin(current, currsch);
        if (nestedLoopsPlan != null) possiblePlans.add(nestedLoopsPlan);
        // productJoin is always worse than nestedLoopJoin if there is a predicate
//        Plan productPlan = makeProductJoin(current, currsch);
//        if (productPlan != null) possiblePlans.add(productPlan);

        return possiblePlans.stream().min(Comparator.comparingInt(Plan::blocksAccessed)).orElse(null);
    }

    /**
     * Constructs a product plan of the specified plan and
     * this table.
     *
     * @param current the specified plan
     * @return a product plan of the specified plan and this table
     */
    public Plan makeProductPlan(Plan current) {
        Plan p = addSelectPred(myplan);
        return new MultibufferProductPlan(tx, current, p);
    }

    private Plan makeIndexSelect() {
        for (String fldname : indexes.keySet()) {
            Constant val = mypred.equatesWithConstant(fldname);
            if (val != null) {
                IndexInfo ii = indexes.get(fldname);
                return new IndexSelectPlan(myplan, ii, val);
            }
        }
        return null;
    }

    private Plan makeIndexJoin(Plan current, Schema currsch) {
        for (String fldname : indexes.keySet()) {
            String outerfield = mypred.equatesWithField(fldname);
            if (outerfield != null && currsch.hasField(outerfield)) {
                IndexInfo ii = indexes.get(fldname);
                Plan p = new IndexJoinPlan(current, myplan, ii, outerfield);
                p = addSelectPred(p);
                return addJoinPred(p, currsch, outerfield, fldname);
            }
        }
        return null;
    }

    private Plan makeNestedLoopsJoin(Plan current, Schema currsch) {
        Predicate joinpred = mypred.joinSubPred(myschema, currsch);
        if (joinpred == null)
            return null;
        return new NestedLoopsJoinPlan(current, addSelectPred(myplan), joinpred);
    }

    private Plan makeHashJoin(Plan current, Schema currsch) {
        for (String fldname : myschema.fields()) {
            String outerfield = mypred.equatesWithField(fldname);
            if (outerfield != null && currsch.hasField(outerfield)) {
                Plan p = new HashJoinPlan(tx, current, addSelectPred(myplan), outerfield, fldname);
                p = addJoinPred(p, currsch, outerfield, fldname);
                return p;
            }
        }
        return null;
    }

    private Plan makeMergeJoin(Plan current, Schema currsch) {
        for (String fldname : myschema.fields()) {
            String outerfield = mypred.equatesWithField(fldname);
            if (outerfield != null && currsch.hasField(outerfield)) {
                Plan p = new MergeJoinPlan(tx, current, addSelectPred(myplan), outerfield, fldname);
                p = addJoinPred(p, currsch, outerfield, fldname);
                return p;
            }
        }
        return null;
    }

    private Plan makeProductJoin(Plan current, Schema currsch) {
        Plan p = makeProductPlan(current);
        return addJoinPred(p, currsch);
    }

    private Plan addSelectPred(Plan p) {
        Predicate selectpred = mypred.selectSubPred(myschema);
        if (selectpred != null)
            return new SelectPlan(p, selectpred);
        else
            return p;
    }

    private Plan addJoinPred(Plan p, Schema currsch, String primaryFld1, String primaryFld2) {
        Predicate joinpred = mypred.joinSubPred(currsch, myschema, primaryFld1, primaryFld2);
        if (joinpred != null)
            return new SelectPlan(p, joinpred);
        else
            return p;
    }

    private Plan addJoinPred(Plan p, Schema currsch) {
        Predicate joinpred = mypred.joinSubPred(currsch, myschema);
        if (joinpred != null)
            return new SelectPlan(p, joinpred);
        else
            return p;
    }
}
