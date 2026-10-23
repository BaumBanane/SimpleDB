package simpledb.plan;

import simpledb.metadata.MetadataMgr;
import simpledb.query.*;
import simpledb.tx.Transaction;
import simpledb.parse.*;

/**
 * The object that executes SQL statements.
 *
 * @author Edward Sciore
 */
public class Planner {
    private final QueryPlanner qplanner;
    private final UpdatePlanner uplanner;
    private final MetadataMgr metadataMgr;

    public Planner(QueryPlanner qplanner, UpdatePlanner uplanner, MetadataMgr metadataMgr) {
        this.qplanner = qplanner;
        this.uplanner = uplanner;
        this.metadataMgr = metadataMgr;
    }

    /**
     * Creates a plan for an SQL select statement, using the supplied planner.
     *
     * @param qry the SQL query string
     * @param tx  the transaction
     * @return the scan corresponding to the query plan
     */
    public Plan createQueryPlan(String qry, Transaction tx) {
        Parser parser = new Parser(qry);
        QueryData data = parser.query();
        Validator validator = new Validator(metadataMgr, tx);
        validator.verifyQuery(data);
        return qplanner.createPlan(data, tx);
    }

    /**
     * Executes an SQL insert, delete, modify, or
     * create statement.
     * The method dispatches to the appropriate method of the
     * supplied update planner,
     * depending on what the parser returns.
     *
     * @param cmd the SQL update string
     * @param tx  the transaction
     * @return an integer denoting the number of affected records
     */
    public int executeUpdate(String cmd, Transaction tx) {
        Parser parser = new Parser(cmd);
        Object data = parser.updateCmd();
        Validator validator = new Validator(metadataMgr, tx);
        validator.verifyUpdate(data);
        if (data instanceof InsertData)
            return uplanner.executeInsert((InsertData) data, tx);
        else if (data instanceof DeleteData)
            return uplanner.executeDelete((DeleteData) data, tx);
        else if (data instanceof ModifyData)
            return uplanner.executeModify((ModifyData) data, tx);
        else if (data instanceof CreateTableData)
            return uplanner.executeCreateTable((CreateTableData) data, tx);
        else if (data instanceof CreateViewData)
            return uplanner.executeCreateView((CreateViewData) data, tx);
        else if (data instanceof CreateIndexData)
            return uplanner.executeCreateIndex((CreateIndexData) data, tx);
        else
            return 0;
    }

}
