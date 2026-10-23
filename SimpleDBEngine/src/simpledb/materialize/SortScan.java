package simpledb.materialize;

import java.util.*;

import simpledb.query.*;
import simpledb.record.*;

/**
 * The Scan class for the <i>sort</i> operator.
 * <p>
 * The scan merges any number of sorted runs. Each run has its own
 * underlying scan, and the scan that holds the smallest record is the
 * current scan. Merging k runs here means that SortPlan only has to
 * merge its runs until k of them are left, instead of 2.
 *
 * @author Edward Sciore
 */
public class SortScan implements Scan {
    private final List<UpdateScan> scans = new ArrayList<>();
    private final boolean[] hasmore;
    private final RecordComparator comp;
    private int current = -1;
    private List<RID> savedposition;
    private int savedcurrent = -1;

    /**
     * Create a sort scan, given a list of runs.
     * The list may be empty, in which case the scan has no records.
     *
     * @param runs the list of runs
     * @param comp the record comparator
     */
    public SortScan(List<TempTable> runs, RecordComparator comp) {
        this.comp = comp;
        this.hasmore = new boolean[runs.size()];
        for (int i = 0; i < runs.size(); i++) {
            UpdateScan s = runs.get(i).open();
            scans.add(s);
            hasmore[i] = s.next();
        }
    }

    /**
     * Position the scan before the first record in sorted order.
     * Internally, it moves to the first record of each underlying scan.
     * The current scan is set to none, indicating that there is
     * no current record.
     *
     * @see simpledb.query.Scan#beforeFirst()
     */
    public void beforeFirst() {
        current = -1;
        for (int i = 0; i < scans.size(); i++) {
            UpdateScan s = scans.get(i);
            s.beforeFirst();
            hasmore[i] = s.next();
        }
    }

    /**
     * Move to the next record in sorted order.
     * First, the current scan is moved to the next record.
     * Then the scan holding the lowest record is found, and that
     * scan becomes the new current scan.
     * Returns false when every run has run out of records.
     *
     * @see simpledb.query.Scan#next()
     */
    public boolean next() {
        if (current >= 0)
            hasmore[current] = scans.get(current).next();

        current = -1;
        for (int i = 0; i < scans.size(); i++) {
            if (!hasmore[i])
                continue;
            if (current < 0 || comp.compare(scans.get(i), scans.get(current)) < 0)
                current = i;
        }
        return current >= 0;
    }

    /**
     * Close the underlying scans.
     *
     * @see simpledb.query.Scan#close()
     */
    public void close() {
        for (UpdateScan s : scans)
            s.close();
    }

    /**
     * Get the Constant value of the specified field
     * of the current scan.
     *
     * @see simpledb.query.Scan#getVal(java.lang.String)
     */
    public Constant getVal(String fldname) {
        return scans.get(current).getVal(fldname);
    }

    /**
     * Get the integer value of the specified field
     * of the current scan.
     *
     * @see simpledb.query.Scan#getInt(java.lang.String)
     */
    public int getInt(String fldname) {
        return scans.get(current).getInt(fldname);
    }

    /**
     * Get the string value of the specified field
     * of the current scan.
     *
     * @see simpledb.query.Scan#getString(java.lang.String)
     */
    public String getString(String fldname) {
        return scans.get(current).getString(fldname);
    }

    /**
     * Return true if the specified field is in the current scan.
     *
     * @see simpledb.query.Scan#hasField(java.lang.String)
     */
    public boolean hasField(String fldname) {
        return scans.get(current).hasField(fldname);
    }

    /**
     * Save the position of the current record of every run,
     * so that they can be restored at a later time.
     * A run that has no more records saves no position.
     */
    public void savePosition() {
        savedposition = new ArrayList<>();
        for (int i = 0; i < scans.size(); i++)
            savedposition.add(hasmore[i] ? scans.get(i).getRid() : null);
        savedcurrent = current;
    }

    /**
     * Move every run to its previously-saved position.
     */
    public void restorePosition() {
        for (int i = 0; i < scans.size(); i++) {
            RID rid = savedposition.get(i);
            if (rid != null) {
                scans.get(i).moveToRid(rid);
                hasmore[i] = true;
            }
        }
        current = savedcurrent;
    }
}
