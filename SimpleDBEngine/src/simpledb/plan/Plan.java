package simpledb.plan;

import simpledb.query.Scan;
import simpledb.record.*;

/**
 * The interface implemented by each query plan.
 * There is a Plan class for each relational algebra operator.
 * @author Edward Sciore
 *
 */
public interface Plan {
   
   /**
    * Opens a scan corresponding to this plan.
    * The scan will be positioned before its first record.
    * @return a scan
    */
   public Scan   open();
   
   /**
    * Returns an estimate of the number of block accesses
    * that will occur when the scan is read to completion.
    * @return the estimated number of block accesses
    */
   public int    blocksAccessed();
   
   /**
    * Returns an estimate of the number of records
    * in the query's output table.
    * @return the estimated number of output records
    */
   public int    recordsOutput();
   
   /**
    * Returns an estimate of the number of distinct values
    * for the specified field in the query's output table.
    * @param fldname the name of a field
    * @return the estimated number of distinct field values in the output
    */
   public int    distinctValues(String fldname);
   
   /**
    * Returns the schema of the query.
    * @return the query's schema
    */
   public Schema schema();

   /**
    * Returns a description of this plan and of the plans below it,
    * one plan per line, each level indented three spaces further than
    * the level above it. A plan appends the description of its
    * underlying plans, so calling this method on the top plan of a
    * query returns the whole query plan.
    * The default implementation describes the plan by its class name
    * and its cost estimates. A plan that has underlying plans should
    * override it so that those plans are shown as well.
    * @param depth the number of levels this plan is below the top plan
    * @return a description of this plan and the plans below it
    */
   public default String planString(int depth) {
      return indent(depth) + getClass().getSimpleName()
           + "  (B=" + blocksAccessed() + ", R=" + recordsOutput() + ")\n";
   }

   /**
    * Returns the indentation that planString uses at the given depth.
    * @param depth the number of levels below the top plan
    * @return a string of three spaces per level
    */
   public static String indent(int depth) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < depth; i++)
         sb.append("   ");
      return sb.toString();
   }
}
