package simpledb.test;

import simpledb.tx.Transaction;
import simpledb.metadata.MetadataMgr;
import simpledb.plan.Plan;
import simpledb.plan.TablePlan;
import simpledb.materialize.HashJoinPlan;
import simpledb.query.Scan;
import simpledb.record.Schema;

/**
 * Plan 2 of the join experiment: the same <b>left-deep</b> join order as
 * {@link IndexMultiJoinPlan}, but using a {@link HashJoinPlan} at every
 * level. Running Plans 1 and 2 under an identical join order isolates the
 * effect of the join <i>algorithm</i>.
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
 * <p>Implements {@link Plan} by delegating to the root of the built tree.
 * Edit the table and field name constants below if your database uses a
 * different schema.
 */
public class HashMultiJoinPlan implements Plan {
   private final Plan plan;   // root of the built left-deep tree

   public HashMultiJoinPlan(Transaction tx, MetadataMgr md) {
      Plan student = new TablePlan(tx, "student", md);
      Plan enroll  = new TablePlan(tx, "enroll",  md);
      Plan section = new TablePlan(tx, "section", md);
      Plan course  = new TablePlan(tx, "course",  md);

      Plan p1 = new HashJoinPlan(tx, student, enroll,  "sid",       "studentid");
      Plan p2 = new HashJoinPlan(tx, p1,      section, "sectionid", "sectid");
      Plan p3 = new HashJoinPlan(tx, p2,      course,  "courseid",  "cid");
      this.plan = p3;
   }

   public Scan open()                        { return plan.open(); }
   public int blocksAccessed()               { return plan.blocksAccessed(); }
   public int recordsOutput()                { return plan.recordsOutput(); }
   public int distinctValues(String fldname) { return plan.distinctValues(fldname); }
   public Schema schema()                    { return plan.schema(); }
   public String planString(int depth)       { return plan.planString(depth); }
}
