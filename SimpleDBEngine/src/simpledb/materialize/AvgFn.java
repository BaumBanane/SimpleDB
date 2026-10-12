package simpledb.materialize;

import simpledb.query.*;

/**
 * The <i>avg</i> aggregation function.
 * The aggregated field must be an integer field.
 * Since SimpleDB only supports integer and string constants,
 * the average is truncated to an integer.
 */
public class AvgFn implements AggregationFn {
   private String fldname;
   private int sum;
   private int count;

   /**
    * Create an avg aggregation function for the specified field.
    * @param fldname the name of the aggregated field
    */
   public AvgFn(String fldname) {
      this.fldname = fldname;
   }

   /**
    * Start a new running sum and count
    * with the current record.
    * @see simpledb.materialize.AggregationFn#processFirst(simpledb.query.Scan)
    */
   public void processFirst(Scan s) {
      sum = s.getInt(fldname);
      count = 1;
   }

   /**
    * Add the current record to the running sum and count.
    * @see simpledb.materialize.AggregationFn#processNext(simpledb.query.Scan)
    */
   public void processNext(Scan s) {
      sum += s.getInt(fldname);
      count++;
   }

   /**
    * Return the field's name, prepended by "avgof".
    * @see simpledb.materialize.AggregationFn#fieldName()
    */
   public String fieldName() {
      return "avgof" + fldname;
   }

   /**
    * Return the current (integer) average.
    * @see simpledb.materialize.AggregationFn#value()
    */
   public Constant value() {
      return new Constant(sum / count);
   }
   
   public String toString() {
      return "avg(" + fldname + ")";
   }
}
