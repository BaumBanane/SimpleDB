package simpledb.parse;
import java.util.Scanner;

public class ParserTest {
   public static void main(String[] args) {
      Scanner sc = new Scanner(System.in);
      System.out.print("Enter an SQL statement: ");
      while (sc.hasNext()) {
         String s = sc.nextLine();
         Parser p = new Parser(s);
         try {
            QueryData qd;
            if (s.startsWith("select")) {
               qd = p.query();
               System.out.println("Fields: " + qd.fields());
               System.out.println("Tables: " + qd.tables());
               System.out.println("Predicate: " + qd.pred());
               System.out.println("Sort fields: " + qd.sortFields());
            }
            else
               p.updateCmd();
            System.out.println("yes");
         }
         catch (BadSyntaxException ex) {
            System.out.println("no");
         }
         System.out.print("Enter an SQL statement: ");
      }
      sc.close();
   }

}


