package simpledb.test;

import simpledb.jdbc.embedded.EmbeddedDriver;
import simpledb.plan.Plan;
import simpledb.plan.Planner;
import simpledb.query.Scan;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.sql.Statement;

public class ChangeMajor {
    public static void main(String[] args) {
        String cmd = "update STUDENT "
                + "set MajorId=30 "
                + "where SName = 'amy'";

//      Driver d = new EmbeddedDriver();
//      String url = "jdbc:simpledb:studentdb";
        SimpleDB db = new SimpleDB("studentdb_server");
        Transaction tx = db.newTx();
        Planner planner = db.planner();
        int p = planner.executeUpdate(cmd, tx);
        System.out.println("Amy is now a drama major.");
        tx.commit();

//      try // (Connection conn = d.connect(url, null); Statement stmt = conn.createStatement())
//      {
//         stmt.executeUpdate(cmd);
//      }
//      catch(SQLException e) {
//         e.printStackTrace();
//      }
    }
}
