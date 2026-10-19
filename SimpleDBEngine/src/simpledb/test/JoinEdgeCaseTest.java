package simpledb.test;

import simpledb.plan.Plan;
import simpledb.plan.Planner;
import simpledb.query.Scan;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;

record QueryRequirement(String query, String requirement) {
}

public class JoinEdgeCaseTest {
    public static void main(String[] args) {
        try {
            SimpleDB db = new SimpleDB("studentdb");
            Transaction tx = db.newTx();
            Planner planner = db.planner();
            QueryRequirement[] testQueries = {
                    // Equality join with matching single ASC ORDER BY on join attribute
                    // Should create MergeJoinPlan and not insert SortPlan
                    new QueryRequirement("select sname, grade from student, enroll where sid = studentid order by studentid", "Should create MergeJoinPlan and not insert SortPlan"),

                    // Equality join with non-matching ORDER BY
                    // Should create HashJoinPlan and add SortPlan
                    new QueryRequirement("select sname, grade from student, enroll where sid = studentid order by sname", "Should create HashJoinPlan and add SortPlan"),

                    // Equality join with matching DESC ORDER BY
                    // Should create HashJoinPlan and add SortPlan
                    new QueryRequirement("select sname, grade from student, enroll where sid = studentid order by studentid desc", "Should create HashJoinPlan and add SortPlan"),

                    // Equality join with multi-attribute ORDER BY
                    // Should create HashJoinPlan and add SortPlan only once below DistinctPlan
                    new QueryRequirement("select sname, grade from student, enroll where sid = studentid order by studentid, grade", "Should create HashJoinPlan and add SortPlan only once below DistinctPlan"),

                    // Equality join with DISTINCT
                    // Should create HashJoinPlan and add SortPlan only once below DistinctPlan
                    new QueryRequirement("select distinct sname, grade from student, enroll where sid = studentid order by studentid", "Should create HashJoinPlan and add SortPlan only once below DistinctPlan"),

                    // Index join with ORDER BY on index join key
                    // Should create IndexJoinPlan
                    new QueryRequirement("select sname, dname from student, dept where majorid = did order by did", "Should create IndexJoinPlan"),

                    // 4-table join with ORDER BY
                    // Should not create MergeJoinPlan
                    new QueryRequirement("select sname, title from student, enroll, section, course where sid = studentid and sectionid = sectid and courseid = cid order by cid", "Should not create MergeJoinPlan"),
            };
            for (QueryRequirement q : testQueries) {
                System.out.println("Query: " + q.query());
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