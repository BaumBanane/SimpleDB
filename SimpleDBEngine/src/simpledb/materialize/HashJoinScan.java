package simpledb.materialize;

import java.util.*;
import simpledb.query.*;
import simpledb.record.RID;

/**
 * The Scan class for the <i>hashjoin</i> operator.
 * Partition pairs are processed one at a time: the build partition
 * is loaded into an in-memory hash table (keyed on the join value,
 * holding record ids), and the probe partition is scanned to probe it.
 */
public class HashJoinScan implements Scan {
   private List<PartitionPair> pairs;
   private PartitionPair pair;
   private int current;
   private UpdateScan probe, build;
   private Map<Constant, List<RID>> table = new HashMap<>();
   private Iterator<RID> matches = Collections.emptyIterator();

   /**
    * A pair of matching partitions, one from each input.
    * The build partition is the smaller of the two.
    */
   public static class PartitionPair {
      TempTable probe, build;
      String probefld, buildfld;

      PartitionPair(TempTable probe, TempTable build, String probefld, String buildfld) {
         this.probe = probe;
         this.build = build;
         this.probefld = probefld;
         this.buildfld = buildfld;
      }
   }

   /**
    * Create a hashjoin scan over the specified partition pairs.
    * @param pairs the partition pairs to join
    */
   public HashJoinScan(List<PartitionPair> pairs) {
      this.pairs = pairs;
      beforeFirst();
   }

   /**
    * Position the scan before the first record,
    * by loading the first partition pair.
    * @see simpledb.query.Scan#beforeFirst()
    */
   public void beforeFirst() {
      if (!pairs.isEmpty())
         loadPartition(0);
   }

   /**
    * Move to the next joined record.
    * If the current probe record has more matches, move the build
    * scan to the next one. Otherwise, advance the probe scan,
    * moving on to the next partition pair when it is exhausted.
    * @see simpledb.query.Scan#next()
    */
   public boolean next() {
      if (pairs.isEmpty())
         return false;
      while (true) {
         if (matches.hasNext()) {
            build.moveToRid(matches.next());
            return true;
         }
         if (probe.next()) {
            List<RID> rids = table.get(probe.getVal(pair.probefld));
            if (rids != null)
               matches = rids.iterator();
         }
         else if (current + 1 < pairs.size())
            loadPartition(current + 1);
         else
            return false;
      }
   }

   /**
    * Close the scan by closing the open partition scans.
    * @see simpledb.query.Scan#close()
    */
   public void close() {
      if (probe != null)
         probe.close();
      if (build != null)
         build.close();
   }

   /**
    * Return the value of the specified field.
    * The value is obtained from whichever partition scan
    * contains the field.
    * @see simpledb.query.Scan#getVal(java.lang.String)
    */
   public Constant getVal(String fldname) {
      if (probe.hasField(fldname))
         return probe.getVal(fldname);
      else
         return build.getVal(fldname);
   }

   /**
    * Return the integer value of the specified field.
    * @see simpledb.query.Scan#getInt(java.lang.String)
    */
   public int getInt(String fldname) {
      if (probe.hasField(fldname))
         return probe.getInt(fldname);
      else
         return build.getInt(fldname);
   }

   /**
    * Return the string value of the specified field.
    * @see simpledb.query.Scan#getString(java.lang.String)
    */
   public String getString(String fldname) {
      if (probe.hasField(fldname))
         return probe.getString(fldname);
      else
         return build.getString(fldname);
   }

   /**
    * Return true if the specified field is in
    * either of the underlying scans.
    * @see simpledb.query.Scan#hasField(java.lang.String)
    */
   public boolean hasField(String fldname) {
      return probe != null && (probe.hasField(fldname) || build.hasField(fldname));
   }

   private void loadPartition(int i) {
      close();
      current = i;
      pair = pairs.get(i);
      table.clear();
      matches = Collections.emptyIterator();
      build = pair.build.open();
      while (build.next())
         table.computeIfAbsent(build.getVal(pair.buildfld), v -> new ArrayList<>())
              .add(build.getRid());
      probe = pair.probe.open();
   }
}
