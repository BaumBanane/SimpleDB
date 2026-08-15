package simpledb.test;

import simpledb.jdbc.embedded.EmbeddedDriver;
import simpledb.plan.Plan;
import simpledb.plan.Planner;
import simpledb.query.Scan;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Scanner;

public class FindMajors {
    public static void main(String[] args) {
        System.out.print("Enter a department name: ");
        Scanner sc = new Scanner(System.in);
        String major = sc.next();
        sc.close();
        System.out.println("Here are the " + major + " majors");
        System.out.println("Name\tGradYear");

//      String url = "jdbc:simpledb:studentdb";
        String qry = "select sname, gradyear "
                + "from student, dept "
                + "where did = majorid "
                + "and dname = '" + major + "'"; // this is not a prepared statement

//      Driver d = new EmbeddedDriver();
        SimpleDB db = new SimpleDB("studentdb_server");
        try
        //Connection conn = d.connect(url, null);
        //Statement stmt = conn.createStatement();
        //ResultSet rs = stmt.executeQuery(qry)
        {
            Transaction tx = db.newTx();
            Planner planner = db.planner();
            Plan p = planner.createQueryPlan(qry, tx);
            Scan s = p.open();

            while (s.next()) {
                String sname = s.getString("sname");
                int gradyear = s.getInt("gradyear");
                System.out.println(sname + "\t" + gradyear);
            }
            s.close();
            tx.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
