package simpledb.plan;

import simpledb.query.Scan;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;

public class SortTest {
    public static void main(String[] args) {
        SimpleDB db = new SimpleDB("sorttest");
        Transaction tx = db.newTx();
        Planner planner = db.planner();
        String cmd = "create table T1(A int, B varchar(9))";
        planner.executeUpdate(cmd, tx);

        int n = 200;
        System.out.println("Inserting " + n + " records.");
        for (int i=0; i<n; i++) {
            int a = i;
            String b = "rec" + a;
            cmd = "insert into T1(A,B) values(" + a + ", '" + b + "')";
            planner.executeUpdate(cmd, tx);
        }

        String qry = "select B from T1 order by A";
        Plan p = planner.createQueryPlan(qry, tx);
        Scan s = p.open();
        while (s.next())
            System.out.println(s.getString("b"));
        s.close();
        tx.commit();
    }
}
