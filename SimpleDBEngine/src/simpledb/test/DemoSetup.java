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
import java.sql.Types;

public class DemoSetup {
    public static void main(String[] args) {
        setupDemoDB();
        String[] testQueries = {
                "select sname, dname from student, dept where majorid=did",
        };
        String[] lab2IndexSelect = {
                "select sname from student where majorid = 10 and sname != 'stu41'",
                "select did, dname from dept where did = 10", // btree
        };
        String[] lab4Joins = {
                "select sname, dname from student, dept where majorid = did", // index
                "select sname, title from student, course where majorid > deptid", // nested loops
                "select title, prof from course, section where cid = courseid", // merge - equality with no index
                "select title, prof from course, section where cid = courseid order by cid" // merge - ordering
        };
        String[] lab6JoinWithAndWithoutDuplicates = {
                "select dname from student, dept where majorid=did",
                "select distinct dname from student, dept where majorid=did",
                "select dname, gradyear from student, dept where majorid = did", // duplicates compared across full tuple
                "select distinct dname, gradyear from student, dept where majorid = did"
        };
        String[] lab3OrderBy3WayJoin = {
                // courses a student could not have taken
                "select sname, gradyear, yearoffered, prof, title from student, section, course where gradyear < yearoffered and courseid = cid",
                "select sname, gradyear, yearoffered, prof, title from student, section, course where gradyear < yearoffered and courseid = cid order by sname",
        };
        String[] lab5Aggregate = {
                "select sname, count(sectionid) from student, enroll where sid = studentid group by sname",
                "select count(cid) from course",
        };

        String[] bonusValidateQueries = {
                "INSERT INTO student (sid, sname, majorid) VALUES (1, 'alice', 10)",
                "select majorid, count(sid) FROM student GROUP BY majorid ORDER BY count(sid)",
                "select distinct grade from enroll order by studentid",
                "select majorid from student group by majorid order by sname",
                "SELECT majorid FROM student GROUP BY majorid ORDER BY count(sid)",
        };
        String[] bonusMultiwaySort = {
            "select sname, gradyear from student order by gradyear desc, sname asc", // sorting
            "select title, prof from course, section where cid = courseid" // join - neither table is indexed


        };
        runQueries(lab4Joins);
    }

    private static void runQueries(String[] testQueries) {
        try {
            SimpleDB db = new SimpleDB("demo");
            Planner planner = db.planner();
            for (String q : testQueries) {
                Transaction tx = db.newTx();
                long start = System.currentTimeMillis();
                doQuery(planner, tx, q);
                long end = System.currentTimeMillis();
                System.out.println("Query execution took " + (end - start) + " ms");
                System.out.println();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void setupDemoDB() {
        File dbDir = new File("demo");
        if (dbDir.exists()) {
            deleteDirectory(dbDir);
        }

        insertData();
    }

    private static void insertData() {
        SimpleDB db = new SimpleDB("demo");
        Transaction tx = db.newTx();
        Planner planner = db.planner();

        String s = "create table STUDENT(SId int, SName varchar(10), MajorId int, GradYear int)";
        planner.executeUpdate(s, tx);
        System.out.println("Table STUDENT created.");

        s = "create index idxMajorId on student(majorid)";
        planner.executeUpdate(s, tx);
        System.out.println("index created: " + s);

        s = "insert into STUDENT(SId, SName, MajorId, GradYear) values ";
        // Original 9 students
        String[] studvals = {
                "(1, 'joe', 10, 2021)", "(2, 'amy', 20, 2020)", "(3, 'max', 10, 2022)",
                "(4, 'sue', 20, 2022)", "(5, 'bob', 30, 2020)", "(6, 'kim', 20, 2020)",
                "(7, 'art', 30, 2021)", "(8, 'pat', 20, 2019)", "(9, 'lee', 10, 2021)"
        };
        for (String studval : studvals) planner.executeUpdate(s + studval, tx);

        // Generate 91 more
        for (int i = 10; i <= 100; i++) {
            int major = (i % 3 == 0) ? 30 : ((i % 2 == 0) ? 20 : 10);
            int year = 2019 + (i % 4);
            planner.executeUpdate(s + "(" + i + ", 'stu" + i + "', " + major + ", " + year + ")", tx);
        }
        System.out.println("STUDENT records inserted (100 total).");

        s = "create table DEPT(DId int, DName varchar(8))";
        planner.executeUpdate(s, tx);
        System.out.println("Table DEPT created.");

        s = "create index idxDId on dept(did) using btree";
        planner.executeUpdate(s, tx);
        System.out.println("index created: " + s);

        s = "insert into DEPT(DId, DName) values ";
        String[] deptvals = {
                "(10, 'compsci')",
                "(20, 'math')",
                "(30, 'drama')"
        };
        for (String deptval : deptvals) planner.executeUpdate(s + deptval, tx);
        System.out.println("3 DEPT records inserted.");

        s = "create table COURSE(CId int, Title varchar(20), DeptId int)";
        planner.executeUpdate(s, tx);
        System.out.println("Table COURSE created.");

        s = "insert into COURSE(CId, Title, DeptId) values ";
        String[] coursevals = {
                // Original 6
                "(12, 'db systems', 10)", "(22, 'compilers', 10)",
                "(32, 'calculus', 20)", "(42, 'algebra', 20)",
                "(52, 'acting', 30)", "(62, 'elocution', 30)",
                // New 14
                "(72, 'os', 10)", "(82, 'networks', 10)",
                "(92, 'ai', 10)", "(102, 'graphics', 10)",
                "(112, 'robotics', 10)", "(122, 'geometry', 20)",
                "(132, 'topology', 20)", "(142, 'logic', 20)",
                "(152, 'stats', 20)", "(162, 'directing', 30)",
                "(172, 'lighting', 30)", "(182, 'costumes', 30)",
                "(192, 'staging', 30)", "(202, 'improv', 30)"
        };
        for (String courseval : coursevals) planner.executeUpdate(s + courseval, tx);
        System.out.println("20 COURSE records inserted.");

        s = "create table SECTION(SectId int, CourseId int, Prof varchar(8), YearOffered int)";
        planner.executeUpdate(s, tx);
        System.out.println("Table SECTION created.");

        s = "insert into SECTION(SectId, CourseId, Prof, YearOffered) values ";
        String[] sectvals = {
                // Original 5
                "(13, 12, 'turing', 2018)", "(23, 12, 'turing', 2019)",
                "(33, 32, 'newton', 2019)", "(43, 32, 'einstein', 2017)",
                "(53, 62, 'brando', 2018)",
                // New 15 mapping to the new courses
                "(63, 22, 'lovelace', 2020)", "(73, 42, 'euclid', 2019)",
                "(83, 52, 'streep', 2021)", "(93, 72, 'dijkstra', 2021)",
                "(103, 82, 'turing', 2022)", "(113, 92, 'lovelace', 2020)",
                "(123, 102, 'dijkstra', 2019)", "(133, 112, 'turing', 2021)",
                "(143, 122, 'euler', 2018)", "(153, 132, 'cantor', 2019)",
                "(163, 142, 'gauss', 2022)", "(173, 152, 'einstein', 2021)",
                "(183, 162, 'pacino', 2020)", "(193, 172, 'brando', 2019)",
                "(203, 182, 'streep', 2022)"
        };
        for (String sectval : sectvals) planner.executeUpdate(s + sectval, tx);
        System.out.println("20 SECTION records inserted.");

        s = "create table ENROLL(EId int, StudentId int, SectionId int, Grade varchar(2))";
        planner.executeUpdate(s, tx);
        System.out.println("Table ENROLL created.");

        s = "create index idxEnrollStudent on enroll(studentid)";
        planner.executeUpdate(s, tx);
        System.out.println("index created: " + s);

        s = "insert into ENROLL(EId, StudentId, SectionId, Grade) values ";
        // Original 6 enrollments
        String[] enrollvals = {
                "(14, 1, 13, 'A')", "(24, 1, 43, 'C' )",
                "(34, 2, 43, 'B+')", "(44, 4, 33, 'B' )",
                "(54, 4, 53, 'A' )", "(64, 6, 53, 'A' )"
        };
        for (String enrollval : enrollvals) planner.executeUpdate(s + enrollval, tx);

        // Add 94 more enrollments
        for (int i = 7; i <= 100; i++) {
            int eId = i * 10 + 4;            // 74, 84, 94...
            int sectId = (i % 20) * 10 + 13; // cycles through the 20 actual section IDs
            String grade = (i % 3 == 0) ? "'A'" : ((i % 2 == 0) ? "'B'" : "'C'");
            planner.executeUpdate(s + "(" + eId + ", " + i + ", " + sectId + ", " + grade + ")", tx);
        }
        System.out.println("100 ENROLL records inserted.");

        tx.commit();
    }

    private static void doQuery(Planner planner, Transaction tx, String cmd) {
        try {
            Plan p = planner.createQueryPlan(cmd, tx);
            System.out.println("\nQuery plan:");
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
            while (s.next()) {
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
            }
            s.close();
            tx.commit();
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
