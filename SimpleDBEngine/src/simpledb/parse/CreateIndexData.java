package simpledb.parse;

/**
 * The parser for the <i>create index</i> statement.
 *
 * @author Edward Sciore
 */
public class CreateIndexData {
    private final String idxname;
    private final String tblname;
    private final String fldname;
    private final String idxtype;

    /**
     * Saves the table and field names of the specified index.
     */
    public CreateIndexData(String idxname, String tblname, String fldname, String idxtype) {
        this.idxname = idxname;
        this.tblname = tblname;
        this.fldname = fldname;
        this.idxtype = idxtype;
    }

    /**
     * Returns the type of the index, either "hash" or "btree".
     *
     * @return the type of the index, either "hash" or "btree"
     */
    public String indexType() {
        return idxtype;
    }

    /**
     * Returns the name of the index.
     *
     * @return the name of the index
     */
    public String indexName() {
        return idxname;
    }

    /**
     * Returns the name of the indexed table.
     *
     * @return the name of the indexed table
     */
    public String tableName() {
        return tblname;
    }

    /**
     * Returns the name of the indexed field.
     *
     * @return the name of the indexed field
     */
    public String fieldName() {
        return fldname;
    }
}

