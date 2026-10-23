package simpledb.test;

import simpledb.tx.Transaction;
import simpledb.metadata.MetadataMgr;
import simpledb.plan.Plan;
import simpledb.plan.TablePlan;
import simpledb.materialize.MergeJoinPlan;
import simpledb.query.Scan;
import simpledb.record.Schema;

/**
 * A left-deep join of STUDENT, ENROLL, SECTION and COURSE using a
 * {@link MergeJoinPlan} (sort-merge join) at every level. It is the
 * sort-merge counterpart of {@link HashMultiJoinPlan}: same join order,
 * different algorithm, so the two can be compared under an identical
 * left-deep order.
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
 * <p>Each {@code MergeJoinPlan} sorts both of its inputs on the join field
 * (via an internal {@code SortPlan}) before merging, so this plan does
 * repeated sorting up the tree. Implements {@link Plan} by delegating to
 * the root of the built tree. Edit the table and field name constants
 * below if your database uses a different schema.
 */
public class MergeMultiJoinPlan implements Plan {
   private final Plan plan;   // root of the built left-deep tree

   public MergeMultiJoinPlan(Transaction tx, MetadataMgr md) {
      Plan student = new TablePlan(tx, "student", md);
      Plan enroll  = new TablePlan(tx, "enroll",  md);
      Plan section = new TablePlan(tx, "section", md);
      Plan course  = new TablePlan(tx, "course",  md);

      Plan p1 = new MergeJoinPlan(tx, student, enroll,  "sid",       "studentid");
      Plan p2 = new MergeJoinPlan(tx, p1,      section, "sectionid", "sectid");
      Plan p3 = new MergeJoinPlan(tx, p2,      course,  "courseid",  "cid");
      this.plan = p3;
   }

   public Scan open()                        { return plan.open(); }
   public int blocksAccessed()               { return plan.blocksAccessed(); }
   public int recordsOutput()                { return plan.recordsOutput(); }
   public int distinctValues(String fldname) { return plan.distinctValues(fldname); }
   public Schema schema()                    { return plan.schema(); }
   public String planString(int depth)       { return plan.planString(depth); }
}
