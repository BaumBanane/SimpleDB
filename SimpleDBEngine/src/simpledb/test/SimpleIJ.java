package simpledb.test;

import simpledb.jdbc.embedded.EmbeddedMetaData;
import simpledb.parse.BadSyntaxException;
import simpledb.parse.Parser;
import simpledb.plan.Plan;
import simpledb.plan.Planner;
import simpledb.plan.ValidationException;
import simpledb.plan.Validator;
import simpledb.query.Scan;
import simpledb.record.Schema;
import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;

import java.sql.*;
import java.util.Scanner;

public class SimpleIJ {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.println("Connect> ");
        String s = sc.nextLine();
//      Driver d = (s.contains("//")) ? new NetworkDriver() : new EmbeddedDriver();
        SimpleDB db = new SimpleDB(s.contains("server") ? "studentdb_server" : s.contains("demo") ? "demo" : "studentdb");
        Planner planner = db.planner();
        try
//      (Connection conn = d.connect(s, null);
//           Statement stmt = conn.createStatement())
        {
            System.out.print("\nSQL> ");
            while (sc.hasNextLine()) {
                Transaction tx;
                // process one line of input
                String cmd = sc.nextLine().trim();
                if (cmd.toLowerCase().startsWith("exit"))
                    break;
                else if (cmd.toLowerCase().startsWith("select")) {
                    tx = db.newTx();
                    doQuery(planner, tx, cmd);
                } else {
                    tx = db.newTx();
                    doUpdate(planner, tx, cmd);
                }
                System.out.print("\nSQL> ");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        sc.close();
    }

    private static void doQuery(Planner planner, Transaction tx, String cmd) {
        try //(ResultSet rs = planner.executeQuery(cmd))
        {
            Plan p;
            try {
                p = planner.createQueryPlan(cmd, tx);
            } catch (BadSyntaxException e) {
                Parser.handleBadSyntaxException(e);
                tx.rollback();
                return;
            } catch (ValidationException e) {
                Validator.handleValidationException(e);
                tx.rollback();
                return;
            }
            System.out.println("\nQuery plan:");
            System.out.println(p.planString(1));
            Scan s = p.open();
            Schema sch = p.schema();
            ResultSetMetaData md = new EmbeddedMetaData(sch);
            //    md =  rs.getMetaData();
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

    private static void doUpdate(Planner planner, Transaction tx, String cmd) {
        try {

            int howmany = planner.executeUpdate(cmd, tx);
            System.out.println(howmany + " records processed");
            tx.commit();
        } catch (BadSyntaxException e) {
            Parser.handleBadSyntaxException(e);
            tx.rollback();
            return;
        } catch (ValidationException e) {
            Validator.handleValidationException(e);
            tx.rollback();
            return;
        } catch (Exception e) {
            System.out.println("Exception: " + e.getMessage());
            tx.rollback();
        }
    }
}