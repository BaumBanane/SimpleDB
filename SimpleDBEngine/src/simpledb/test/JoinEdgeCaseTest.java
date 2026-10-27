package simpledb.test;

import simpledb.plan.Plan;
import simpledb.plan.Planner;
import simpledb.query.Scan;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;

record QueryRequirement(String query, String requirement) {
}

public class JoinEdgeCaseTest {
    public static QueryRequirement[] testQueries = {
            // Equality join with matching single ASC ORDER BY on join attribute
            // Should create MergeJoinPlan and not insert SortPlan
            new QueryRequirement("select sname, grade from student, enroll where sid = studentid order by studentid", "Should prefer MergeJoinPlan and not insert SortPlan since output sorted on join attribute"),

            // Equality join with non-matching ORDER BY
            // Should create HashJoinPlan and add SortPlan
            new QueryRequirement("select sname, grade from student, enroll where sid = studentid order by sname", "Should add SortPlan and not prefer MergeJoinPlan since order by is different from join attribute"),

            // Equality join with matching DESC ORDER BY
            // Should create HashJoinPlan and add SortPlan
//                    new QueryRequirement("select sname, grade from student, enroll where sid = studentid order by studentid desc", "Should add SortPlan and not prefer MergeJoinPlan"),

            // DISTINCT and ORDER BY
            // Should create HashJoinPlan and add SortPlan only once below DistinctPlan
            new QueryRequirement("select distinct sname, studentid, grade from student, enroll where sid = studentid order by studentid", "Should create HashJoinPlan and add SortPlan only once below DistinctPlan which fulfills both distinct and order by"),

            // Index join with ORDER BY on index join key
            // Should create IndexJoinPlan
            new QueryRequirement("select sname, dname from student, dept where majorid = did order by did", "Should prefer IndexJoinPlan since there is index on join attribute"),

            // 4-table join with ORDER BY
            // Should not create MergeJoinPlan
            new QueryRequirement("select sname, title from student, enroll, section, course where sid = studentid and sectionid = sectid and cid = courseid order by sid", "Should prefer MergeJoinPlan only for the outermost table, but not sort every time"),

    };

    public static void main(String[] args) {
        try {
            SimpleDB db = new SimpleDB("demo");
            Transaction tx = db.newTx();
            Planner planner = db.planner();

            for (QueryRequirement q : testQueries) {
                System.out.println("\nQuery: " + q.query());
                System.out.println("Requirement: " + q.requirement());
                Plan p = planner.createQueryPlan(q.query(), tx);
                System.out.print(p.planString(1));
                Scan s = p.open();
                int count = 0;
                while (s.next()) {
                    count++;
                }
                s.close();
                System.out.println("Execution worked, " + count + " rows in result");
                System.out.println();
            }
            tx.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}