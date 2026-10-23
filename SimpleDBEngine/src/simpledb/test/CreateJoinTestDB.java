package simpledb.test;

import java.util.Random;
import simpledb.plan.Planner;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;

/**
 * Populates a database for the four-table join experiment run by
 * {@link BushyJoinTest}. It creates STUDENT, DEPT, COURSE, SECTION and
 * ENROLL and fills them with randomly generated, referentially consistent
 * data at the cardinalities below.
 *
 * <pre>
 *   STUDENT  5000   SId 1..5000,     MajorId -> DEPT.DId
 *   DEPT       40   DId 1..40
 *   COURSE    500   CId 1..500,      DeptId  -> DEPT.DId
 *   SECTION  2500   SectId 1..2500,  CourseId-> COURSE.CId   (~5 sections/course)
 *   ENROLL  50000   EId 1..50000,    StudentId-> STUDENT.SId,
 *                                    SectionId-> SECTION.SectId  (~10 per student)
 * </pre>
 *
 * <p>SimpleDB's SQL has no multi-row INSERT, so each row is inserted with a
 * separate statement (as in {@link CreateStudentDB}). The transaction is
 * committed every {@link #COMMIT_EVERY} statements so the loader does not
 * hold tens of thousands of locks at once.
 *
 * <p>The indexes that Plan 1 ({@link IndexMultiJoinPlan}) relies on are
 * created immediately after each table and before its rows are inserted,
 * because SimpleDB only maintains an index when the table is subsequently
 * modified.
 *
 * <p>Usage: {@code java simpledb.test.CreateJoinTestDB [dbname]}
 * (defaults to {@code studentdb}). Delete any existing database directory
 * of that name first, and pass the same name to {@code BushyJoinTest}.
 */
public class CreateJoinTestDB {
   private static final int NUM_STUDENTS = 5000;
   private static final int NUM_DEPTS    = 40;
   private static final int NUM_COURSES  = 500;
   private static final int NUM_SECTIONS = 2500;
   private static final int NUM_ENROLLS  = 10000;

   /** Commit and start a fresh transaction after this many statements. */
   private static final int COMMIT_EVERY = 1000;

   private static final String[] GRADES = {"A", "A-", "B+", "B", "B-", "C", "D", "F"};

   private static Planner planner;
   private static SimpleDB db;
   private static Transaction tx;
   private static int opCount = 0;

   public static void main(String[] args) {
      String dbname = (args.length > 0) ? args[0] : "studentdb_test";
      Random rnd = new Random(42);          // fixed seed -> reproducible data
      long start = System.currentTimeMillis();

      db = new SimpleDB(dbname);
      planner = db.planner();
      tx = db.newTx();

      // ---- STUDENT ----
      exec("create table STUDENT(SId int, SName varchar(10), MajorId int, GradYear int)");
      for (int sid = 1; sid <= NUM_STUDENTS; sid++) {
         int majorid  = 1 + rnd.nextInt(NUM_DEPTS);
         int gradyear = 2018 + rnd.nextInt(7);
         exec("insert into STUDENT(SId, SName, MajorId, GradYear) values ("
              + sid + ", 's" + sid + "', " + majorid + ", " + gradyear + ")");
      }
      System.out.println(NUM_STUDENTS + " STUDENT rows inserted.");

      // ---- DEPT ----
      exec("create table DEPT(DId int, DName varchar(8))");
      for (int did = 1; did <= NUM_DEPTS; did++)
         exec("insert into DEPT(DId, DName) values (" + did + ", 'dept" + did + "')");
      System.out.println(NUM_DEPTS + " DEPT rows inserted.");

      // ---- COURSE (index on cid for Plan 1's top-level index join) ----
      exec("create table COURSE(CId int, Title varchar(20), DeptId int)");
      exec("create index idxCId on COURSE(cid)");
      for (int cid = 1; cid <= NUM_COURSES; cid++) {
         int deptid = 1 + rnd.nextInt(NUM_DEPTS);
         exec("insert into COURSE(CId, Title, DeptId) values ("
              + cid + ", 'course" + cid + "', " + deptid + ")");
      }
      System.out.println(NUM_COURSES + " COURSE rows inserted.");

      // ---- SECTION (index on sectid for Plan 1's middle index join) ----
      exec("create table SECTION(SectId int, CourseId int, Prof varchar(8), YearOffered int)");
      exec("create index idxSectId on SECTION(sectid)");
      for (int sectid = 1; sectid <= NUM_SECTIONS; sectid++) {
         int courseid = 1 + rnd.nextInt(NUM_COURSES);
         int year     = 2015 + rnd.nextInt(10);
         exec("insert into SECTION(SectId, CourseId, Prof, YearOffered) values ("
              + sectid + ", " + courseid + ", 'prof" + (1 + rnd.nextInt(200)) + "', " + year + ")");
      }
      System.out.println(NUM_SECTIONS + " SECTION rows inserted.");

      // ---- ENROLL (index on studentid for Plan 1's first index join) ----
      exec("create table ENROLL(EId int, StudentId int, SectionId int, Grade varchar(2))");
      exec("create index idxStudentId on ENROLL(studentid)");
      for (int eid = 1; eid <= NUM_ENROLLS; eid++) {
         int studentid = 1 + rnd.nextInt(NUM_STUDENTS);
         int sectionid = 1 + rnd.nextInt(NUM_SECTIONS);
         String grade  = GRADES[rnd.nextInt(GRADES.length)];
         exec("insert into ENROLL(EId, StudentId, SectionId, Grade) values ("
              + eid + ", " + studentid + ", " + sectionid + ", '" + grade + "')");
         if (eid % 10000 == 0)
            System.out.println("   ... " + eid + " ENROLL rows");
      }
      System.out.println(NUM_ENROLLS + " ENROLL rows inserted.");

      tx.commit();
      long secs = (System.currentTimeMillis() - start) / 1000;
      System.out.println("Done populating '" + dbname + "' in " + secs + " s.");
   }

   /**
    * Executes one update statement, committing and renewing the
    * transaction every COMMIT_EVERY statements to keep lock and log
    * usage bounded while loading tens of thousands of rows.
    */
   private static void exec(String sql) {
      planner.executeUpdate(sql, tx);
      if (++opCount % COMMIT_EVERY == 0) {
         tx.commit();
         tx = db.newTx();
      }
   }
}
