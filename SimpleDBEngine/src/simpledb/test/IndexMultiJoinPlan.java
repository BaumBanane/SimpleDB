package simpledb.test;

import simpledb.tx.Transaction;
import simpledb.metadata.MetadataMgr;
import simpledb.metadata.IndexInfo;
import simpledb.plan.Plan;
import simpledb.plan.TablePlan;
import simpledb.index.planner.IndexJoinPlan;
import simpledb.materialize.HashJoinPlan;
import simpledb.query.Scan;
import simpledb.record.Schema;

/**
 * Plan 1 of the join experiment: a <b>left-deep</b> join of
 * STUDENT, ENROLL, SECTION and COURSE that uses an
 * {@link IndexJoinPlan} at each level when an index on the
 * right-hand join field is available, and falls back to a
 * {@link HashJoinPlan} for any level whose index is missing.
 *
 * <p>The logical join order is
 * <pre>(((STUDENT &#8904; ENROLL) &#8904; SECTION) &#8904; COURSE)</pre>
 * with the joins
 * <pre>
 *   STUDENT.sid        = ENROLL.studentid
 *   ENROLL.sectionid   = SECTION.sectid
 *   SECTION.courseid   = COURSE.cid
 * </pre>
 *
 * <p>Because the tree is left-deep, the newly added table is always the
 * right-hand input, so it is always a leaf {@link TablePlan} &mdash; which
 * is exactly what {@code IndexJoinPlan} requires. This class implements
 * {@link Plan} by delegating to the root of the tree it builds, so it can
 * be timed and printed just like any other plan.
 *
 * <p>Edit the table and field name constants below if your database uses
 * a different schema.
 */
public class IndexMultiJoinPlan implements Plan {
   private final Plan plan;   // root of the built left-deep tree

   public IndexMultiJoinPlan(Transaction tx, MetadataMgr md) {
      Plan student = new TablePlan(tx, "student", md);
      Plan p1 = indexJoin(tx, md, student, "enroll",  "sid",       "studentid");
      Plan p2 = indexJoin(tx, md, p1,      "section", "sectionid", "sectid");
      Plan p3 = indexJoin(tx, md, p2,      "course",  "courseid",  "cid");
      this.plan = p3;
   }

   /**
    * Joins the left-hand plan to a base table. Uses an index join when an
    * index on the table's join field exists, otherwise a hash join so the
    * plan still runs. {@code lhsField} is the join field coming from the
    * left-hand plan; {@code rhsField} is the matching field on the table.
    */
   private Plan indexJoin(Transaction tx, MetadataMgr md, Plan lhs,
                          String rhsTbl, String lhsField, String rhsField) {
      Plan rhs = new TablePlan(tx, rhsTbl, md);
      IndexInfo ii = md.getIndexInfo(rhsTbl, tx).get(rhsField);
      if (ii != null)
         return new IndexJoinPlan(lhs, rhs, ii, lhsField);
      System.out.println("   [no index on " + rhsTbl + "." + rhsField
                         + " -- using hash join for this level]");
      return new HashJoinPlan(tx, lhs, rhs, lhsField, rhsField);
   }

   public Scan open()                        { return plan.open(); }
   public int blocksAccessed()               { return plan.blocksAccessed(); }
   public int recordsOutput()                { return plan.recordsOutput(); }
   public int distinctValues(String fldname) { return plan.distinctValues(fldname); }
   public Schema schema()                    { return plan.schema(); }
   public String planString(int depth)       { return plan.planString(depth); }
}
