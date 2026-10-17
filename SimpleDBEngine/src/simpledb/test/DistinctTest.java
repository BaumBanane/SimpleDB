package simpledb.test;

import simpledb.plan.Plan;
import simpledb.plan.Planner;
import simpledb.query.Scan;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;

/* Runs queries with and without the distinct keyword on the student database.
 * The query plan is printed before each result which shows where the
 * distinct and sort operators are in the plan.
 */

public class DistinctTest {
   public static void main(String[] args) {
      try {
         SimpleDB db = new SimpleDB("studentdb_server");
         Transaction tx = db.newTx();
         Planner planner = db.planner();

         // without distinct: one record per student (expect 9 rows)
         doQuery(planner, tx, "select majorid from student", 9);

         // one field: the three departments that students major in (expect 3 rows)
         doQuery(planner, tx, "select distinct majorid from student", 3);

         // many fields: duplicates are the records that match on both fields (expect 7 rows)
         doQuery(planner, tx, "select distinct majorid, gradyear from student", 7);

         // distinct with order by: the sort is done after the duplicates
         // have been removed so the output stays sorted (expect 4 rows)
         doQuery(planner, tx,
                 "select distinct gradyear from student order by gradyear desc", 4);

         // distinct over a join: 9 students in 3 departments (expect 3 rows)
         doQuery(planner, tx,
                 "select distinct dname from student, dept where majorid = did", 3);

         tx.commit();
      }
      catch(Exception e) {
         e.printStackTrace();
      }
   }

   /* Runs the query, prints its plan and its records, and reports whether
    * the number of records is the number expected.
    */
   private static void doQuery(Planner planner, Transaction tx, String qry, int expected) {
      System.out.println("\n" + qry);
      Plan p = planner.createQueryPlan(qry, tx);
      System.out.print(p.planString(1));

      Scan s = p.open();
      int count = 0;
      while (s.next()) {
         String row = "";
         for (String fldname : p.schema().fields())
            row += s.getVal(fldname) + "\t";
         System.out.println("   " + row);
         count++;
      }
      s.close();
      System.out.println("   " + count + " rows, expected " + expected
                        + (count == expected ? "  CORRECT" : "  WRONG"));
   }
}
