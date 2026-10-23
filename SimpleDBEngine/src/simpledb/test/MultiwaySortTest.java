package simpledb.test;

import java.io.File;
import java.util.Random;

import simpledb.plan.Plan;
import simpledb.plan.Planner;
import simpledb.query.Constant;
import simpledb.query.Scan;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;

/* Measures the work done by the sort operator.
 *
 * The program fills a table with records in random order, sorts it with an
 * order by clause, and reports how many temporary tables the sort created
 * and how long it took. The number of temporary tables is the number of
 * initial runs plus the number of runs written by every merge pass, so it
 * shows how much copying the merge phase does.
 *
 * It also checks that the output really is in ascending order.
 *
 * Usage: MultiwaySortTest [number of records]   (default 400)
 *
 * The program creates and deletes its own database, "multiwaysorttest",
 * so it can be run as often as needed.
 */

public class MultiwaySortTest {
   private static final String DBNAME = "multiwaysorttest";
   private static final int DEFAULT_SIZE = 400;
   private static final int SEED = 42;

   public static void main(String[] args) {
      int numrecs = (args.length > 0) ? Integer.parseInt(args[0]) : DEFAULT_SIZE;
      try {
         deleteDatabase();
         SimpleDB db = new SimpleDB(DBNAME);
         Transaction tx = db.newTx();
         Planner planner = db.planner();

         createTable(planner, tx, numrecs);
         int tempsBefore = countTempTables();

         String qry = "select a, b from testsort order by a";
         System.out.println("\n" + qry);
         Plan p = planner.createQueryPlan(qry, tx);
         System.out.print(p.planString(1));

         long start = System.currentTimeMillis();
         Scan s = p.open();
         int count = 0;
         boolean sorted = true;
         Constant prev = null;
         while (s.next()) {
            Constant cur = s.getVal("a");
            if (prev != null && prev.compareTo(cur) > 0)
               sorted = false;
            prev = cur;
            count++;
         }
         s.close();
         long ms = System.currentTimeMillis() - start;

         int tempsAfter = countTempTables();
         System.out.println();
         System.out.println("   records out:     " + count + " (expected " + numrecs + ")"
                            + (count == numrecs ? "  CORRECT" : "  WRONG"));
         System.out.println("   ascending order: " + (sorted ? "CORRECT" : "WRONG"));
         System.out.println("   temp tables:     " + (tempsAfter - tempsBefore));
         System.out.println("   time:            " + ms + " ms");
         System.out.println("   buffers:         " + tx.availableBuffs() + " available");

         tx.commit();
      }
      catch(Exception e) {
         e.printStackTrace();
      }
   }

   /* Create the table and fill it with records whose sort field is in
    * random order, so that the sort has to build many initial runs.
    * The random numbers are seeded, so every run uses the same data.
    */
   private static void createTable(Planner planner, Transaction tx, int numrecs) {
      planner.executeUpdate("create table TESTSORT(a int, b varchar(9))", tx);
      Random rnd = new Random(SEED);
      for (int i = 0; i < numrecs; i++) {
         int a = rnd.nextInt(numrecs);
         planner.executeUpdate(
            "insert into TESTSORT(a,b) values(" + a + ", 'rec" + a + "')", tx);
      }
      System.out.println("TESTSORT created: " + numrecs + " records in random order");
   }

   /* Count the temporary tables in the database directory. Each merge of
    * runs writes its output to a new temporary table.
    */
   private static int countTempTables() {
      File dir = new File(DBNAME);
      String[] names = dir.list();
      if (names == null)
         return 0;
      int count = 0;
      for (String name : names)
         if (name.startsWith("temp") && name.endsWith(".tbl"))
            count++;
      return count;
   }

   /* Delete the database directory, so that the program can be rerun
    * without the tables being created twice.
    */
   private static void deleteDatabase() {
      File dir = new File(DBNAME);
      File[] files = dir.listFiles();
      if (files != null)
         for (File f : files)
            f.delete();
      dir.delete();
   }
}
