package simpledb.materialize;

import java.util.*;

import simpledb.query.*;

/**
 * The Scan class for the <i>distinct</i> operator.
 * DistinctScan
 */

public class DistinctScan implements Scan {
    private Scan s;
    private List<SortField> fields;
    private GroupValue prev;

    public DistinctScan(Scan s, List<SortField> fields) {
        this.s = s;
        this.fields = fields;
        beforeFirst();
    }

    /**
    * Position the scan before its first record. A
    * subsequent call to next() will return the first record.
    */
    public void beforeFirst() {
      s.beforeFirst();
      prev = null;
    }

    /**
     * Move to the next record that is not a duplicate.
     * The underlying scan is sorted on the distinct fields, so any
     * duplicate of the previous record comes immediately after it.
     * Records are therefore skipped until one differs from the
     * previous record, whose values are kept in prev.
     * Returns false when the underlying records run out.
     */
    public boolean next() {
        while (s.next()) {
            GroupValue cur = new GroupValue(s , fields);
            if (prev == null || !prev.equals(cur)) {
                prev = cur;
                return true;
            }
        }
        return false;
    }

    /**
     * Close the underlying scan.
     */
    public void close() {
        s.close();
    }

    /**
     * Return the integer value of the specified field
     * of the current record.
     */
    public int getInt(String fldname) {
        return s.getInt(fldname);
    }

    /**
     * Return the string value of the specified field
     * of the current record.
     */
    public String getString(String fldname) {
        return s.getString(fldname);
    }

    /**
     * Return the value of the specified field
     * of the current record.
     */
    public Constant getVal(String fldname) {
        return s.getVal(fldname);
    }

    /**
     * Return true if the specified field is in the underlying scan.
     * Distinct removes records, so the fields are unchanged.
     */
    public boolean hasField(String fldname) {
        return s.hasField(fldname);
    }
}
