package simpledb.materialize;

import java.util.*;

import simpledb.query.*;

/**
 * A comparator for scans.
 * @author Edward Sciore
 */
public class RecordComparator implements Comparator<Scan> {
   private List<SortField> fields;
   
   /**
    * Create a comparator using the specified fields,
    * using the ordering specified for each field.
    * @param fields a list of {@code SortField} objects
    */
   public RecordComparator(List<SortField> fields) {
      this.fields = fields;
   }
   
   /**
    * Compare the current records of the two specified scans.
    * The sort fields are considered in turn.
    * When a field is encountered for which the records have
    * different values, those values are used as the result
    * of the comparison.
    * If the two records have the same values for all
    * sort fields, then the method returns 0.
    * @param s1 the first scan
    * @param s2 the second scan
    * @return the result of comparing each scan's current record according to the field list
    */
   public int compare(Scan s1, Scan s2) {
      for (SortField field: fields) {
         Constant val1 = s1.getVal(field.field());
         Constant val2 = s2.getVal(field.field());
         int result = field.isAscending() ? val1.compareTo(val2) : val2.compareTo(val1);
         if (result != 0)
            return result;
      }
      return 0;
   }

   /**
    * Compare two records that are held in memory instead of in scans.
    * The fields are considered in the same order, and in the same
    * direction, as when two scans are compared, so runs that are sorted
    * with this method can be merged with the other compare method.
    * @param r1 the first record, as a map from field name to value
    * @param r2 the second record
    * @return the result of comparing the two records
    */
   public int compare(Map<String,Constant> r1, Map<String,Constant> r2) {
      for (SortField field: fields) {
         Constant val1 = r1.get(field.field());
         Constant val2 = r2.get(field.field());
         int result = field.isAscending() ? val1.compareTo(val2) : val2.compareTo(val1);
         if (result != 0)
            return result;
      }
      return 0;
   }
}
