package simpledb.query;

import java.util.*;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;
import simpledb.record.*;

/**
 * Tests a two-table join pipeline using a field-to-field, non-equality predicate.
 */
public class ScanTest4 {
    public static void main(String[] args) throws Exception {
        SimpleDB db = new SimpleDB("scantest4");
        Transaction tx = db.newTx();

        Schema sch1 = new Schema();
        sch1.addIntField("A");
        sch1.addStringField("B", 9);
        Layout layout1 = new Layout(sch1);
        UpdateScan us1 = new TableScan(tx, "T1", layout1);
        us1.beforeFirst();
        int n = 200;
        System.out.println("Inserting " + n + " records into T1.");
        for (int i=0; i<n; i++) {
            us1.insert();
            us1.setInt("A", i);
            us1.setString("B", "bbb"+i);
        }
        us1.close();

        Schema sch2 = new Schema();
        sch2.addIntField("C");
        sch2.addStringField("D", 9);
        Layout layout2 = new Layout(sch2);
        UpdateScan us2 = new TableScan(tx, "T2", layout2);
        us2.beforeFirst();
        System.out.println("Inserting " + n + " records into T2.");
        for (int i=0; i<n; i++) {
            us2.insert();
            us2.setInt("C", n-i-1);
            us2.setString("D", "ddd"+(n-i-1));
        }
        us2.close();

        Scan s1 = new TableScan(tx, "T1", layout1);
        Scan s2 = new TableScan(tx, "T2", layout2);
        Scan s3 = new ProductScan(s1, s2);
        // selecting all records where A<C
        Term t = new Term(new Expression("A"), new Expression("C"), "<");
        Predicate pred = new Predicate(t);
        System.out.println("The predicate is " + pred);
        Scan s4 = new SelectScan(s3, pred);

        // projecting on [B,D]
        List<String> c = Arrays.asList("B", "D");
        Scan s5 = new ProjectScan(s4, c);
        int matchCount = 0;
        while (s5.next()) {
            matchCount++;
        }
        s5.close();

        // the expected count is the number of pairs (i,j) with i,j in [0,199] and i<j.
        int expected = n * (n - 1) / 2;
        System.out.println("Matches found: " + matchCount);
        System.out.println("Expected matches: " + expected);

        tx.commit();
    }
}
