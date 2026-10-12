package simpledb.materialize;

import java.util.*;
import simpledb.tx.Transaction;
import simpledb.materialize.HashJoinScan.PartitionPair;
import simpledb.plan.Plan;
import simpledb.query.*;
import simpledb.record.*;


/**
 * The Plan class for the partition-based <i>hashjoin</i> operator.
 * Both inputs are partitioned on their join field into k temporary
 * tables using the same hash function, so matching records always
 * end up in partitions with the same index.
 * A partition pair whose smaller side is too big to fit in memory
 * is recursively re-partitioned using a different hash function.
 */
public class HashJoinPlan implements Plan {
   private static final int MAX_LEVEL = 3;
   private Transaction tx;
   private Plan p1, p2;
   private String fldname1, fldname2;
   private Schema sch = new Schema();

   /**
    * Creates a hashjoin plan for the two specified queries.
    * @param tx the calling transaction
    * @param p1 the LHS query plan
    * @param p2 the RHS query plan
    * @param fldname1 the LHS join field
    * @param fldname2 the RHS join field
    */
   public HashJoinPlan(Transaction tx, Plan p1, Plan p2, String fldname1, String fldname2) {
      this.tx = tx;
      this.p1 = p1;
      this.p2 = p2;
      this.fldname1 = fldname1;
      this.fldname2 = fldname2;
      sch.addAll(p1.schema());
      sch.addAll(p2.schema());
   }

   /**
    * Partitions both inputs into k temporary tables,
    * where k is limited by the number of available buffers
    * (one output buffer per partition). Partition pairs that
    * are too big to fit in memory are re-partitioned, and the
    * resulting pairs are passed to a hashjoin scan.
    * @see simpledb.plan.Plan#open()
    */
   public Scan open() {
      int k = Math.max(1, tx.availableBuffs() - 2); //reserve a couple
      List<Partition> parts1 = partition(p1.open(), p1.schema(), fldname1, k, 0);
      List<Partition> parts2 = partition(p2.open(), p2.schema(), fldname2, k, 0);
      List<PartitionPair> pairs = new ArrayList<>();
      for (int i = 0; i < k; i++)
         addPairs(parts1.get(i), parts2.get(i), k, 1, Integer.MAX_VALUE, pairs);
      return new HashJoinScan(pairs);
   }

   /**
    * Return the number of block accesses required to
    * hashjoin the two inputs: each input is read once,
    * written once into its partitions, and read once more
    * during the probe phase.
    * This assumes that no partition needs to be re-partitioned.
    * @see simpledb.plan.Plan#blocksAccessed()
    */
   public int blocksAccessed() {
      Plan mp1 = new MaterializePlan(tx, p1); // not opened; just for analysis
      Plan mp2 = new MaterializePlan(tx, p2);
      return 3 * (mp1.blocksAccessed() + mp2.blocksAccessed());
   }

   /**
    * Return the number of records in the join.
    * Assuming uniform distribution, the formula is:
    * <pre> R(join(p1,p2)) = R(p1)*R(p2)/max{V(p1,F1),V(p2,F2)}</pre>
    * @see simpledb.plan.Plan#recordsOutput()
    */
   public int recordsOutput() {
      int maxvals = Math.max(p1.distinctValues(fldname1),
                             p2.distinctValues(fldname2));
      return (p1.recordsOutput() * p2.recordsOutput()) / maxvals;
   }

   /**
    * Estimate the distinct number of field values in the join.
    * Since the join does not increase or decrease field values,
    * the estimate is the same as in the appropriate underlying query.
    * @see simpledb.plan.Plan#distinctValues(java.lang.String)
    */
   public int distinctValues(String fldname) {
      if (p1.schema().hasField(fldname))
         return p1.distinctValues(fldname);
      else
         return p2.distinctValues(fldname);
   }

   /**
    * Return the schema of the join,
    * which is the union of the schemas of the underlying queries.
    * @see simpledb.plan.Plan#schema()
    */
   public Schema schema() {
      return sch;
   }

   /**
    * The partitioning hash function for the specified level.
    * Each level uses a different seed, so records that collided
    * at one level are spread out at the next. The murmur3 finalizer
    * also makes it differ from the hash used by the in-memory
    * hash table built during the probe phase.
    */
   static int partitionOf(Constant val, int k, int level) {
      int h = val.hashCode() ^ (0x9E3779B9 * (level + 1));
      h ^= h >>> 16;
      h *= 0x85EBCA6B;
      h ^= h >>> 13;
      h *= 0xC2B2AE35;
      h ^= h >>> 16;
      return Math.floorMod(h, k);
   }

   /**
    * Adds the pairs needed to join the two specified partitions.
    * The smaller partition is used as the build side. If even the
    * smaller partition does not fit in memory, both partitions are
    * re-partitioned with the next level's hash function and each
    * sub-pair is handled recursively. Re-partitioning stops at
    * MAX_LEVEL, or when it fails to shrink the build side.
    * @param k the number of partitions, which is also
    *          the number of blocks that fit in memory
    * @param level the hash function level for re-partitioning
    * @param parentsize the build side size of the enclosing pair
    */
   private void addPairs(Partition r, Partition s, int k, int level, int parentsize,
                         List<PartitionPair> pairs) {
      if (r.records == 0 || s.records == 0)
         return; // no record can match
      Partition build = (s.blocks() <= r.blocks()) ? s : r;
      Partition probe = (build == s) ? r : s;
      boolean fits = build.blocks() <= k;
      boolean shrank = build.blocks() < parentsize;
      if (fits || !shrank || level > MAX_LEVEL || k == 1) {
         pairs.add(new PartitionPair(probe.table, build.table,
                                                  probe.fldname, build.fldname));
         return;
      }
      List<Partition> rs = partition(r.table.open(), r.table.getLayout().schema(),
                                     r.fldname, k, level);
      List<Partition> ss = partition(s.table.open(), s.table.getLayout().schema(),
                                     s.fldname, k, level);
      for (int i = 0; i < k; i++)
         addPairs(rs.get(i), ss.get(i), k, level + 1, build.blocks(), pairs);
   }

   private List<Partition> partition(Scan src, Schema psch, String fldname, int k, int level) {
      List<Partition> parts = new ArrayList<>();
      List<UpdateScan> dests = new ArrayList<>();
      for (int i = 0; i < k; i++) {
         Partition part = new Partition(new TempTable(tx, psch), fldname);
         parts.add(part);
         dests.add(part.table.open());
      }
      src.beforeFirst();
      while (src.next()) {
         int i = partitionOf(src.getVal(fldname), k, level);
         UpdateScan dest = dests.get(i);
         dest.insert();
         for (String f : psch.fields())
            dest.setVal(f, src.getVal(f));
         parts.get(i).records++;
      }
      src.close();
      for (UpdateScan dest : dests)
         dest.close();
      return parts;
   }

   /**
    * A partition of one of the inputs, stored in a temporary table.
    */
   private class Partition {
      TempTable table;
      String fldname;
      int records = 0;

      Partition(TempTable table, String fldname) {
         this.table = table;
         this.fldname = fldname;
      }

      int blocks() {
         return tx.size(table.tableName() + ".tbl");
      }
   }
}
