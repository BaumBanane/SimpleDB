package simpledb.test;

import java.util.Arrays;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;
import simpledb.metadata.MetadataMgr;
import simpledb.plan.Plan;
import simpledb.plan.TablePlan;
import simpledb.materialize.HashJoinPlan;
import simpledb.query.Scan;

/**
 * Join experiment for the four-table query over STUDENT, ENROLL, SECTION
 * and COURSE. It times three plans with a multi-run harness:
 * <ol>
 *   <li>Plan 1 &mdash; left-deep, index join ({@link IndexMultiJoinPlan})</li>
 *   <li>Plan 2 &mdash; left-deep, hash join  ({@link HashMultiJoinPlan})</li>
 *   <li>Plan 3 &mdash; bushy, hash join (built inline below):
 *       <pre>(STUDENT &#8904; ENROLL) &#8904; (SECTION &#8904; COURSE)</pre></li>
 * </ol>
 *
 * <p><b>How timing works.</b> In SimpleDB much of a join's work happens in
 * {@code open()} (hash partitioning, materializing, sorting), so each run
 * times {@code open()} <i>and</i> draining the scan to completion. Rows are
 * only counted, never printed, so console I/O does not pollute the
 * measurement. Alongside wall-clock time the harness prints the
 * cache-independent cost estimate {@code blocksAccessed()}.
 *
 * <p><b>Cache warning.</b> All runs share one buffer pool, so the first run
 * of each plan warms the cache and is excluded from the statistics; the
 * reported min/median/mean are therefore <i>warm-cache</i> numbers. For
 * cold-cache numbers, run each plan from a freshly started engine (comment
 * out the other two calls and relaunch) and read the "warm-up" line.
 *
 * <p>Usage: {@code java simpledb.test.BushyJoinTest [dbname]}
 * (defaults to {@code studentdb}).
 */
public class BushyJoinTest {
   /** Total runs per plan; the first is a warm-up and is excluded. */
   private static final int RUNS = 5;

   public static void main(String[] args) {
      String dbname = (args.length > 0) ? args[0] : "studentdb_teset";
      try {
         SimpleDB db = new SimpleDB(dbname);
         MetadataMgr md = db.mdMgr();
         Transaction tx = db.newTx();

         // Plan 4 - bushy tree, hash join at every node.
         // Two independent sub-joins are combined by the top join:
         //   left  = STUDENT.sid      = ENROLL.studentid
         //   right = SECTION.courseid = COURSE.cid
         //   top   = ENROLL.sectionid = SECTION.sectid
         Plan student = new TablePlan(tx, "student", md);
         Plan enroll  = new TablePlan(tx, "enroll",  md);
         Plan section = new TablePlan(tx, "section", md);
         Plan course  = new TablePlan(tx, "course",  md);
         Plan left  = new HashJoinPlan(tx, student, enroll,  "sid",       "studentid");
         Plan right = new HashJoinPlan(tx, section, course,  "courseid",  "cid");
         Plan bushy = new HashJoinPlan(tx, left,    right,   "sectionid", "sectid");

         timePlan("PLAN 1  -  left-deep, index join", new IndexMultiJoinPlan(tx, md));
         timePlan("PLAN 2  -  left-deep, hash join",  new HashMultiJoinPlan(tx, md));
         timePlan("PLAN 3  -  left-deep, merge join",  new MergeMultiJoinPlan(tx, md));
         timePlan("PLAN 4  -  bushy, hash join",      bushy);

         tx.commit();
      }
      catch (Exception e) {
         e.printStackTrace();
      }
   }

   /** Prints a plan's tree and cost estimate, then times RUNS executions. */
   private static void timePlan(String label, Plan p) {
      System.out.println();
      System.out.println("============================================================");
      System.out.println(label);
      System.out.println("============================================================");
      System.out.print(p.planString(1));
      System.out.println("estimated cost:  B(blocksAccessed)=" + p.blocksAccessed()
                        + "   R(recordsOutput)=" + p.recordsOutput());

      long[] ms = new long[RUNS];
      int rows = 0;
      for (int i = 0; i < RUNS; i++) {
         long start = System.nanoTime();
         Scan s = p.open();
         int count = 0;
         while (s.next())
            count++;
         s.close();
         ms[i] = (System.nanoTime() - start) / 1_000_000;
         rows = count;
         System.out.printf("   run %-2d: %6d ms%s%n", i + 1, ms[i],
                           i == 0 ? "   (warm-up, excluded)" : "");
      }
      report(ms, rows);
   }

   /** Reports min/median/mean/max over every run except the warm-up (index 0). */
   private static void report(long[] ms, int rows) {
      long[] timed = Arrays.copyOfRange(ms, 1, ms.length);  // drop warm-up
      Arrays.sort(timed);
      int n = timed.length;
      long min = timed[0];
      long max = timed[n - 1];
      double mean = 0;
      for (long v : timed)
         mean += v;
      mean /= n;
      double median = (n % 2 == 1) ? timed[n / 2]
                                   : (timed[n / 2 - 1] + timed[n / 2]) / 2.0;
      System.out.printf("   rows=%d   |   over %d timed runs:  min=%d ms,"
                      + " median=%.1f ms, mean=%.1f ms, max=%d ms%n",
                        rows, n, min, median, mean, max);
   }
}
