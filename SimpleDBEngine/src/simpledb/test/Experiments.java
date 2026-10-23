package simpledb.test;

import simpledb.jdbc.embedded.EmbeddedMetaData;
import simpledb.plan.Plan;
import simpledb.plan.Planner;
import simpledb.query.Scan;
import simpledb.record.Schema;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;

import java.io.File;
import java.sql.ResultSetMetaData;

public class Experiments {
    public static void main(String[] args) {
//        setupExperimentDB();
//        experiment1();
        experiment2();
    }

    private static void experiment1() {
        try {
            SimpleDB db = new SimpleDB("experiments");
            Planner planner = db.planner();
            String[] testQueries = {
                    "select sname, dname from student, dept where majorid=did"
            };
            for (String q : testQueries) {
                Transaction tx = db.newTx();
                long start = System.currentTimeMillis();
                System.out.println("Executing query: " + q);
                doQuery(planner, tx, q);
                long end = System.currentTimeMillis();
                System.out.println("Query execution took " + (end - start) + " ms\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void experiment2() {
        String[] testQueries = {
                "select sname, grade, prof, title from student, enroll, section, course where sid = studentid and sectionid = sectid and courseid = cid"
        };
        try {
            SimpleDB db = new SimpleDB("experiments");
            Planner planner = db.planner();

            for (String q : testQueries) {
                Transaction tx = db.newTx();
                long start = System.currentTimeMillis();
                System.out.println("Executing query: " + q);
                doQuery(planner, tx, q);
                long end = System.currentTimeMillis();
                System.out.println("Query execution took " + (end - start) + " ms\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void setupExperimentDB() {
        File dbDir = new File("experiments");
        if (dbDir.exists()) {
            deleteDirectory(dbDir);
            System.out.println("Deleted existing 'experiments' directory.");
        }

        System.out.println("Generating 5,000 student dataset. This will take a moment...");
        insertData();
    }

    private static void insertData() {
        SimpleDB db = new SimpleDB("experiments");
        Transaction tx = db.newTx();
        Planner planner = db.planner();

        // ---------------------------------------------------------
        // 1. STUDENT (5,000 records)
        // ---------------------------------------------------------
        String s = "create table STUDENT(SId int, SName varchar(10), MajorId int, GradYear int)";
        planner.executeUpdate(s, tx);
        System.out.println("Table STUDENT created.");

        s = "create index idxMajorId on student(majorid)";
        planner.executeUpdate(s, tx);
        System.out.println("Index created: " + s);

        s = "insert into STUDENT(SId, SName, MajorId, GradYear) values ";
        String[] studvals = {
                "(1, 'joe', 10, 2021)", "(2, 'amy', 20, 2020)", "(3, 'max', 10, 2022)",
                "(4, 'sue', 20, 2022)", "(5, 'bob', 30, 2020)", "(6, 'kim', 20, 2020)",
                "(7, 'art', 30, 2021)", "(8, 'pat', 20, 2019)", "(9, 'lee', 10, 2021)"
        };
        for (String studval : studvals) planner.executeUpdate(s + studval, tx);

        for (int i = 10; i <= 5000; i++) {
            int major = ((i % 50) + 1) * 10; // Distribute across 50 majors
            int year = 2019 + (i % 4);
            planner.executeUpdate(s + "(" + i + ", 'stu" + i + "', " + major + ", " + year + ")", tx);
        }
        System.out.println("5,000 STUDENT records inserted.\n");

        // ---------------------------------------------------------
        // 2. DEPT (50 records)
        // ---------------------------------------------------------
        s = "create table DEPT(DId int, DName varchar(8))";
        planner.executeUpdate(s, tx);
        System.out.println("Table DEPT created.");

        s = "insert into DEPT(DId, DName) values ";
        String[] deptvals = {
                "(10, 'compsci')", "(20, 'math')", "(30, 'drama')"
        };
        for (String deptval : deptvals) planner.executeUpdate(s + deptval, tx);

        for (int i = 4; i <= 50; i++) {
            int dId = i * 10;
            planner.executeUpdate(s + "(" + dId + ", 'dept" + i + "')", tx);
        }
        System.out.println("50 DEPT records inserted.\n");

        // ---------------------------------------------------------
        // 3. COURSE (1,000 records)
        // ---------------------------------------------------------
        s = "create table COURSE(CId int, Title varchar(20), DeptId int)";
        planner.executeUpdate(s, tx);
        System.out.println("Table COURSE created.");

        s = "insert into COURSE(CId, Title, DeptId) values ";
        String[] coursevals = {
                "(12, 'db systems', 10)", "(22, 'compilers', 10)", "(32, 'calculus', 20)",
                "(42, 'algebra', 20)", "(52, 'acting', 30)", "(62, 'elocution', 30)",
                "(72, 'os', 10)", "(82, 'networks', 10)", "(92, 'ai', 10)",
                "(102, 'graphics', 10)", "(112, 'robotics', 10)", "(122, 'geometry', 20)",
                "(132, 'topology', 20)", "(142, 'logic', 20)", "(152, 'stats', 20)",
                "(162, 'directing', 30)", "(172, 'lighting', 30)", "(182, 'costumes', 30)",
                "(192, 'staging', 30)", "(202, 'improv', 30)"
        };
        for (String courseval : coursevals) planner.executeUpdate(s + courseval, tx);

        for (int i = 21; i <= 1000; i++) {
            int cId = i * 10 + 2;
            int deptId = ((i % 50) + 1) * 10;
            planner.executeUpdate(s + "(" + cId + ", 'crs" + i + "', " + deptId + ")", tx);
        }
        System.out.println("1,000 COURSE records inserted.\n");

        // ---------------------------------------------------------
        // 4. SECTION (1,000 records)
        // ---------------------------------------------------------
        s = "create table SECTION(SectId int, CourseId int, Prof varchar(8), YearOffered int)";
        planner.executeUpdate(s, tx);
        System.out.println("Table SECTION created.");

        s = "insert into SECTION(SectId, CourseId, Prof, YearOffered) values ";
        String[] sectvals = {
                "(13, 12, 'turing', 2018)", "(23, 12, 'turing', 2019)",
                "(33, 32, 'newton', 2019)", "(43, 32, 'einstein', 2017)",
                "(53, 62, 'brando', 2018)", "(63, 22, 'lovelace', 2020)",
                "(73, 42, 'euclid', 2019)", "(83, 52, 'streep', 2021)",
                "(93, 72, 'dijkstra', 2021)", "(103, 82, 'turing', 2022)",
                "(113, 92, 'lovelace', 2020)", "(123, 102, 'dijkstra', 2019)",
                "(133, 112, 'turing', 2021)", "(143, 122, 'euler', 2018)",
                "(153, 132, 'cantor', 2019)", "(163, 142, 'gauss', 2022)",
                "(173, 152, 'einstein', 2021)", "(183, 162, 'pacino', 2020)",
                "(193, 172, 'brando', 2019)", "(203, 182, 'streep', 2022)"
        };
        for (String sectval : sectvals) planner.executeUpdate(s + sectval, tx);

        for (int i = 21; i <= 1000; i++) {
            int sectId = i * 10 + 3;
            int courseId = i * 10 + 2;
            int year = 2019 + (i % 4);
            planner.executeUpdate(s + "(" + sectId + ", " + courseId + ", 'prof" + (i % 100) + "', " + year + ")", tx);
        }
        System.out.println("1,000 SECTION records inserted.\n");

        // ---------------------------------------------------------
        // 5. ENROLL (5,000 records)
        // ---------------------------------------------------------
        s = "create table ENROLL(EId int, StudentId int, SectionId int, Grade varchar(2))";
        planner.executeUpdate(s, tx);
        System.out.println("Table ENROLL created.");

        s = "create index idxEnrollStudent on enroll(studentid)";
        planner.executeUpdate(s, tx);
        System.out.println("Index created: " + s);

        s = "insert into ENROLL(EId, StudentId, SectionId, Grade) values ";
        String[] enrollvals = {
                "(14, 1, 13, 'A')", "(24, 1, 43, 'C' )", "(34, 2, 43, 'B+')",
                "(44, 4, 33, 'B' )", "(54, 4, 53, 'A' )", "(64, 6, 53, 'A' )"
        };
        for (String enrollval : enrollvals) planner.executeUpdate(s + enrollval, tx);

        for (int i = 7; i <= 5000; i++) {
            int eId = i * 10 + 4;
            int sectId = (i % 1000) * 10 + 13; // Cycles safely through the 1k section IDs
            String grade = (i % 3 == 0) ? "'A'" : ((i % 2 == 0) ? "'B'" : "'C'");
            planner.executeUpdate(s + "(" + eId + ", " + i + ", " + sectId + ", " + grade + ")", tx);
        }
        System.out.println("5,000 ENROLL records inserted.\n");

        tx.commit();
        System.out.println("Database population complete and committed.\n");
    }

    private static void doQuery(Planner planner, Transaction tx, String cmd) {
        try {
            Plan p = planner.createQueryPlan(cmd, tx);
            System.out.println("Query plan:");
            System.out.println(p.planString(1));
            Scan s = p.open();
            Schema sch = p.schema();
            ResultSetMetaData md = new EmbeddedMetaData(sch);
            int numcols = md.getColumnCount();
            int totalwidth = 0;

            // print header
            for (int i = 1; i <= numcols; i++) {
                String fldname = md.getColumnName(i);
                int width = md.getColumnDisplaySize(i);
                totalwidth += width;
                String fmt = "%" + width + "s";
                System.out.format(fmt, fldname);
            }
            System.out.println();
            for (int i = 0; i < totalwidth; i++)
                System.out.print("-");
            System.out.println();

            // print records
            int recordCount = 0;
            while (s.next()) {
                recordCount++;
                // Uncomment to print result sets
                /*
                for (int i = 1; i <= numcols; i++) {
                    String fldname = md.getColumnName(i);
                    int fldtype = md.getColumnType(i);
                    String fmt = "%" + md.getColumnDisplaySize(i);
                    if (fldtype == Types.INTEGER) {
                        int ival = s.getInt(fldname);
                        System.out.format(fmt + "d", ival);
                    } else {
                        String sval = s.getString(fldname);
                        System.out.format(fmt + "s", sval);
                    }
                }
                System.out.println();
                */
            }
            s.close();
            tx.commit();
            System.out.println("\n(Returned " + recordCount + " records)");
        } catch (Exception e) {
            System.out.println("Exception: " + e.getMessage());
            e.printStackTrace();
            tx.rollback();
        }
    }

    private static void deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        dir.delete();
    }
}