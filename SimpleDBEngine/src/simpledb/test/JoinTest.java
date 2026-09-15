package simpledb.test;

import simpledb.plan.Plan;
import simpledb.plan.Planner;
import simpledb.query.Scan;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;

/* Runs a query for each join algorithm on student database.
 */

public class JoinTest {
   public static void main(String[] args) {
      try {
         SimpleDB db = new SimpleDB("studentdb_server");
         Transaction tx = db.newTx();
         Planner planner = db.planner();

         // Index join: equality join, index on student.majorid (should show 9 rows)
         doQuery(planner, tx, "select sname, dname from student, dept where majorid = did",
                 "sname", "dname");

         // Merge join: equality join, no index on sid or studentid (should show 6 rows)
         doQuery(planner, tx, "select sname, grade from student, enroll where sid = studentid",
                 "sname", "grade");

         // Nested-loops join: non-equality join (should show 8 rows)
         doQuery(planner, tx, "select sname, dname from student, dept where majorid > did",
                 "sname", "dname");

         tx.commit();
      }
      catch(Exception e) {
         e.printStackTrace();
      }
   }

   private static void doQuery(Planner planner, Transaction tx, String qry,
                               String fld1, String fld2) {
      System.out.println("\n" + qry);
      Plan p = planner.createQueryPlan(qry, tx);
      Scan s = p.open();
      int count = 0;
      while (s.next()) {
         System.out.println(s.getString(fld1) + "\t" + s.getString(fld2));
         count++;
      }
      s.close();
      System.out.println(count + " rows");
   }
}
