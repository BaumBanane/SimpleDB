package simpledb.test;

import simpledb.plan.Plan;
import simpledb.plan.Planner;
import simpledb.query.Scan;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;

/* Runs aggregate queries, with and without group by, on student database.
 */

public class AggregateTest {
   public static void main(String[] args) {
      try {
         SimpleDB db = new SimpleDB("studentdb_server");
         Transaction tx = db.newTx();
         Planner planner = db.planner();

         // No group by: one row (9, 2022, amy, 2020, 18186)
         doQuery(planner, tx, "select count(sid), max(gradyear), min(sname), avg(gradyear), sum(gradyear) from student",
                 "countofsid", "maxofgradyear", "minofsname", "avgofgradyear", "sumofgradyear");

         // Group by: (10, 3), (20, 4), (30, 2)
         doQuery(planner, tx, "select majorid, count(sid) from student group by majorid",
                 "majorid", "countofsid");

         // Join + group by + order by: (math, 4), (drama, 2), (compsci, 3)
         doQuery(planner, tx, "select dname, count(sid) from student, dept where majorid = did group by dname order by dname desc",
                 "dname", "countofsid");

         // String min survives sorting: (2019, pat), (2020, amy), (2021, art), (2022, max)
         doQuery(planner, tx, "select gradyear, min(sname) from student group by gradyear order by gradyear",
                 "gradyear", "minofsname");

         // Hash join + group by: (amy, 1), (joe, 2), (kim, 1), (sue, 2)
         doQuery(planner, tx, "select sname, count(eid) from student, enroll where sid = studentid group by sname",
                 "sname", "countofeid");

         // Order by aggregate: (20, 4), (10, 3), (30, 2)
         doQuery(planner, tx, "select majorid, count(sid) from student group by majorid order by count(sid) desc",
                 "majorid", "countofsid");

         tx.commit();
      }
      catch(Exception e) {
         e.printStackTrace();
      }
   }

   private static void doQuery(Planner planner, Transaction tx, String qry, String... flds) {
      System.out.println("\n" + qry);
      Plan p = planner.createQueryPlan(qry, tx);
      Scan s = p.open();
      int count = 0;
      while (s.next()) {
         StringBuilder row = new StringBuilder();
         for (String fld : flds)
            row.append(s.getVal(fld)).append("\t");
         System.out.println(row.toString().trim());
         count++;
      }
      s.close();
      System.out.println(count + " rows");
   }
}
