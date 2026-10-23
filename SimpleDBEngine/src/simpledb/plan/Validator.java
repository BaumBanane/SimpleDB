package simpledb.plan;

import simpledb.materialize.AggregationFn;
import simpledb.metadata.IndexInfo;
import simpledb.metadata.MetadataMgr;
import simpledb.metadata.TableMgr;
import simpledb.metadata.ViewMgr;
import simpledb.parse.*;
import simpledb.query.*;
import simpledb.record.Layout;
import simpledb.record.Schema;
import simpledb.record.TableScan;
import simpledb.tx.Transaction;

import java.sql.Types;
import java.util.*;

public class Validator {
    private final MetadataMgr metadataMgr;
    private final Transaction tx;

    public Validator(MetadataMgr metadataMgr, Transaction tx) {
        this.metadataMgr = metadataMgr;
        this.tx = tx;
    }


    public static void handleValidationException(ValidationException e) {
        System.out.println("Validation error: " + e.getMessage());
    }

    // SimpleDB does not verify queries, although it should.
    public void verifyQuery(QueryData data) {
        verifyNoDuplicateTables(data.tables());

        // the following function will fail if one of the tables doesn't exist
        Schema fromSchema = getCombinedSchema(data.tables());

        // check select fields, accept aggregates for now
        verifyFieldsExistInSchema(data, fromSchema);

        verifyPredicateTypes(data.pred(), fromSchema);

        // the following function also checks aggregates
        verifyGroupByAndAggregates(data, fromSchema);

        verifySortFieldsExist(data, fromSchema);
        verifyDistinctHasAllOrderFields(data);
    }

    private static void verifySortFieldsExist(QueryData data, Schema fromSchema) {
        for (SortField sortField : data.sortFields()) {
            // in aggregate or group by queries, order by fields must either be in the group by clause or be an aggregate in the select clause
            // because SimpleDB only evaluates aggregates from the select clause
            if (isAggregateQuery(data) || isGroupByQuery(data)) {
                if (!hasAggregationFunction(data, sortField.field()) && !hasGroupByField(data, sortField.field()))
                    throw new ValidationException("order by field " + sortField.field() + " not found");
                // in normal queries, order by fields must just be part of the schema (must exist)
            } else if (!fromSchema.hasField(sortField.field()))
                throw new ValidationException("order by field " + sortField.field() + " not found");
        }
    }

    private static void verifyFieldsExistInSchema(QueryData data, Schema fromSchema) {
        for (String field : data.fields()) {
            boolean isAggregate = data.aggregates().stream().anyMatch(a -> a.fieldName().equals(field));
            // e.g. count(sid) would be stored as countofsid in the fields, and this will also not be in tables
            if (!isAggregate && !fromSchema.hasField(field))
                throw new ValidationException("Field " + field + " not found in input tables");
        }
    }

    // this is necessary, because when doing the following query, it is ambiguous from what sid-pairing the grades should be sorted:
    // select distinct grade from enroll order by studentid
    private static void verifyDistinctHasAllOrderFields(QueryData data) {
        if (!data.isDistinct()) return;
        for (SortField sortField : data.sortFields()) {
            if (!data.fields().contains(sortField.field()))
                throw new ValidationException("When selecting distinct, all order by fields must be in output to prevent ambiguity. The input query is missing " + sortField.field() + " in the select list");
        }
    }

    private static void verifyNoDuplicateTables(Collection<String> tables) {
        Set<String> seen = new HashSet<>();
        for (String table : tables) {
            if (!seen.add(table.toLowerCase()))
                throw new ValidationException("Duplicate table " + table + " in from clause");
        }
    }

    private static void verifyGroupByAndAggregates(QueryData data, Schema fromSchema) {
        if (!isGroupByQuery(data) && !isAggregateQuery(data)) return;

        Set<String> groupFields = new HashSet<>();
        for (SortField groupField : data.groupFields()) {
            if (!fromSchema.hasField(groupField.field()))
                throw new ValidationException("group by field " + groupField.field() + " is not in any table in from");
            groupFields.add(groupField.field());
        }

        // Aggregates
        Set<String> aggregateOutputNames = new HashSet<>();
        for (AggregationFn aggregationFn : data.aggregates()) {
            String aggregateField = aggregationFn.fieldArgument();
            if (!fromSchema.hasField(aggregateField))
                throw new ValidationException("Field " + aggregateField + " in aggregate " + aggregationFn + " not found");
            if ((aggregationFn.fieldName().startsWith("sumof") || aggregationFn.fieldName().startsWith("avgof")) && fromSchema.type(aggregateField) != Types.INTEGER)
                throw new ValidationException("Cannot calculate " + aggregationFn + " since " + aggregateField + " is not integer field but " + typeToString(fromSchema.type(aggregateField)));

            aggregateOutputNames.add(aggregationFn.fieldName());
        }

        for (String field : data.fields()) {
            if (!aggregateOutputNames.contains(field) && !groupFields.contains(field)) {
                // cannot output a field that is neither grouped by nor an aggregate, since it might have multiple values
                throw new ValidationException("Field " + field + " must appear in the group by clause");
            }
        }
    }

    private static void verifyPredicateTypes(Predicate predicate, Schema schema) {
        for (Term term : predicate.getTerms()) {
            int leftType = getExpressionType(term.getLhs(), schema);
            int rightType = getExpressionType(term.getRhs(), schema);

            if (leftType != rightType) {
                throw new ValidationException("cannot compare " + typeToString(leftType) + " with " + typeToString(rightType) + " in term " + term);
            }
        }
    }

    private static int getConstantType(Constant value) {
        if (value.asString() != null) return Types.VARCHAR;
        try {
            value.asInt();
            return Types.INTEGER;
        } catch (NullPointerException n) {
            throw new ValidationException("Unknown type of constant " + value);
        }
    }

    private static String typeToString(int type) {
        return switch (type) {
            case Types.INTEGER -> "Integer";
            case Types.VARCHAR -> "Varchar";
            default -> "Unknown Type with value " + type;
        };
    }

    public void verifyUpdate(Object data) {
        if (data instanceof InsertData) {
            verifyInsert((InsertData) data);
        } else if (data instanceof DeleteData) {
            verifyDelete((DeleteData) data);
        } else if (data instanceof ModifyData) {
            verifyModify((ModifyData) data);
        } else if (data instanceof CreateTableData) {
            verifyCreateTable((CreateTableData) data);
        } else if (data instanceof CreateViewData) {
            verifyCreateView((CreateViewData) data);
        } else if (data instanceof CreateIndexData) {
            verifyCreateIndex((CreateIndexData) data);
        } else {
            throw new ValidationException("query was parsed as update, but none of the known update types, instead " + data.getClass());
        }
    }


    private void verifyInsert(InsertData data) {
        String tablename = data.tableName();
        if (!hasTable(tablename)) throw new ValidationException("Table " + tablename + " does not exist");
        Schema schema = metadataMgr.getLayout(tablename, tx).schema();

        List<String> fields = data.fields();
        List<Constant> values = data.vals();
        // SimpleDB only allows one record per insert, so we need as many values as fields
        if (fields.size() != values.size())
            throw new ValidationException("Found " + fields.size() + " fields but " + values + " values, these numbers must be same");

        if (fields.size() != schema.fields().size())
            throw new ValidationException("found " + fields.size() + " fields but table " + tablename + " has " + schema.fields().size() + " fields. found " + fields + " but needed " + schema.fields());

        Set<String> seenFields = new HashSet<>();
        for (int i = 0; i < fields.size(); i++) {
            String field = fields.get(i);
            if (!schema.hasField(field))
                throw new ValidationException("Field " + field + " not found in table " + tablename);

            boolean isDuplicate = !seenFields.add(field);
            if (isDuplicate) throw new ValidationException("Duplicate field " + field);

            Constant value = values.get(i);
            int valueType = getConstantType(value);
            int fieldType = schema.type(field);
            if (valueType != fieldType)
                throw new ValidationException("Type error: Field " + field + " has type " + typeToString(fieldType) + ", but value " + value + " has type " + typeToString(valueType));
            if (fieldType == Types.VARCHAR && value.asString().length() > schema.length(field))
                throw new ValidationException("Varchar value " + value.asString() + " is " + value.asString().length() + " long, exceeding max of " + schema.length(field) + " for field " + field);
        }
    }

    private void verifyDelete(DeleteData data) {
        String tablename = data.tableName();
        if (!hasTable(tablename)) throw new ValidationException("Table " + tablename + " not found");
        verifyPredicateTypes(data.pred(), metadataMgr.getLayout(tablename, tx).schema());
    }

    private void verifyModify(ModifyData data) {
        String tablename = data.tableName();
        if (!hasTable(tablename)) throw new ValidationException("Table " + tablename + " not found");
        Schema schema = metadataMgr.getLayout(tablename, tx).schema();
        String targetField = data.targetField();
        if (!schema.hasField(targetField))
            throw new ValidationException("Field " + targetField + " not found in table " + tablename);

        int fieldType = schema.type(targetField);
        Expression newValue = data.newValue();
        int valueType = getExpressionType(newValue, schema);
        if (fieldType != valueType)
            throw new ValidationException("Type error: Field " + targetField + " has type " + typeToString(fieldType) + ", but value " + newValue + " has type " + typeToString(valueType));
        if (fieldType == Types.VARCHAR) {
            if (newValue.isFieldName()) {
                String srcField = newValue.asFieldName();
                if (schema.length(srcField) > schema.length(targetField))
                    throw new ValidationException("Field " + srcField + " is " + schema.length(srcField) + " long, which is longer than " + targetField + " which is only " + schema.length(targetField) + "long, so copying over is not possible");
            } else {
                if (newValue.asConstant().asString().length() > schema.length(targetField))
                    throw new ValidationException("Varchar value " + newValue.asConstant().asString() + " is " + newValue.asConstant().asString().length() + " long, exceeding max of " + schema.length(targetField) + " for field " + targetField);
            }
        }

        verifyPredicateTypes(data.pred(), schema);
    }

    private void verifyCreateTable(CreateTableData data) {
        String tablename = data.tableName();
        verifyNameLength(tablename);
        if (hasTableOrView(tablename)) throw new ValidationException("Table or view " + tablename + " already exists");
        Schema schema = data.newSchema();
        if (schema.fields().isEmpty()) throw new ValidationException("Cannot create table with no fields");

        int recordSize = new Layout(schema).slotSize();
        if (recordSize > tx.blockSize())
            throw new ValidationException("Record size " + recordSize + " too large, block size is " + tx.blockSize());

        Set<String> seen = new HashSet<>();
        for (String field : schema.fields()) {
            verifyNameLength(field);
            boolean isDuplicate = !seen.add(field.toLowerCase());
            if (isDuplicate) throw new ValidationException("Duplicate field " + field.toLowerCase());
            switch (schema.type(field)) {
                case Types.VARCHAR -> {
                    if (schema.length(field) <= 0)
                        throw new ValidationException("VARCHAR length for field " + field + " must be positive, cannot be " + schema.length(field));
                }
                case Types.INTEGER -> {
                }
                default ->
                        System.out.println("Type of field " + field + " not verifiable. Please add case to simpledb/plan/Validator.java:verifyCreateTable()");
            }
        }
    }

    private void verifyCreateView(CreateViewData data) {
        if (data.viewDef().length() > ViewMgr.MAX_VIEWDEF)
            throw new ValidationException("View definition is " + data.viewDef().length() + " long, but maximum is " + ViewMgr.MAX_VIEWDEF);
        String tablename = data.viewName();
        verifyNameLength(tablename);
        if (hasTableOrView(tablename)) throw new ValidationException("Table or view " + tablename + " already exists");

        Parser parser = new Parser(data.viewDef());
        QueryData queryData = parser.query();
        String duplicateField = queryData.fields().stream().filter(field -> Collections.frequency(queryData.fields(), field) > 1).findAny().orElse(null);
        if (duplicateField != null)
            throw new ValidationException("Duplicate field " + duplicateField + " in view definition");

        verifyQuery(queryData);
    }

    private void verifyCreateIndex(CreateIndexData data) {
        verifyNameLength(data.indexName());

        Layout indexTableLayout = metadataMgr.getLayout("idxcat", tx);
        TableScan ts = new TableScan(tx, "idxcat", indexTableLayout);
        while (ts.next()) {
            if (ts.getString("indexname").equals(data.indexName())) {
                ts.close();
                throw new ValidationException("Index " + data.indexName() + " already exists");
            }
        }
        ts.close();

        String tablename = data.tableName();
        if (!hasTable(tablename)) throw new ValidationException("Table " + tablename + " does not exist");
        Schema schema = metadataMgr.getLayout(tablename, tx).schema();
        if (!schema.hasField(data.fieldName()))
            throw new ValidationException("Field " + data.fieldName() + " not found in table " + tablename);

        Map<String, IndexInfo> indexes = metadataMgr.getIndexInfo(tablename, tx);
        if (indexes.containsKey(data.fieldName()))
            throw new ValidationException("Index already exists for field " + data.fieldName() + " on table " + tablename);
    }

    private Schema getSchema(String tablename) {
        if (hasView(tablename)) {
            // we use how it was defined to get its schema
            String viewDef = metadataMgr.getViewDef(tablename, tx);
            Parser parser = new Parser(viewDef);
            QueryData viewData = parser.query();
            return getQuerySchema(viewData);
        }
        if (hasTable(tablename)) {
            Layout layout = metadataMgr.getLayout(tablename, tx);
            return layout.schema();
        }

        throw new ValidationException("Table or view " + tablename + " does not exist");
    }

    private Schema getCombinedSchema(Collection<String> tables) {
        Schema combined = new Schema();
        for (String table : tables) {
            Schema tableSchema = getSchema(table);
            for (String field : tableSchema.fields()) {
                // check if field already exists, since SimpleDB does not like ambiguous fields
                if (combined.hasField(field))
                    throw new ValidationException("Ambiguous field " + field + " in multiple tables in FROM");
                combined.add(field, tableSchema);
            }
        }
        return combined;
    }

    private Schema getQuerySchema(QueryData data) {
        Schema fromSchema = getCombinedSchema(data.tables());
        Schema querySchema = new Schema();
        for (String field : data.fields()) {
            AggregationFn matchingAggregationFn = getAggregationFunction(data, field);
            if (matchingAggregationFn != null) matchingAggregationFn.addToSchema(querySchema, fromSchema);
            else if (fromSchema.hasField(field)) querySchema.add(field, fromSchema);
            else
                throw new ValidationException("requested field " + field + " not found in any of these tables: " + data.tables());
        }
        return querySchema;

    }

    private static boolean hasAggregationFunction(QueryData data, String field) {
        return getAggregationFunction(data, field) != null;
    }

    private static AggregationFn getAggregationFunction(QueryData data, String field) {
        return data.aggregates().stream().filter(fn -> fn.fieldName().equals(field)).findAny().orElse(null);
    }

    private static boolean hasGroupByField(QueryData data, String field) {
        return data.groupFields().stream().anyMatch(fld -> fld.field().equals(field));
    }

    private static boolean isAggregateQuery(QueryData data) {
        return !data.aggregates().isEmpty();
    }

    private static boolean isGroupByQuery(QueryData data) {
        return !data.groupFields().isEmpty();
    }


    private boolean hasTable(String tablename) {
        return metadataMgr.tableExists(tablename, tx);
    }

    private boolean hasView(String viewname) {
        return metadataMgr.viewExists(viewname, tx);
    }

    private boolean hasTableOrView(String tablename) {
        return hasTable(tablename) || hasView(tablename);
    }

    private static void verifyNameLength(String name) {
        if (name.length() > TableMgr.MAX_NAME)
            throw new ValidationException("Entity name " + name + " is too long, maximum length is " + TableMgr.MAX_NAME + " characters");
    }

    private static int getExpressionType(Expression expression, Schema schema) {
        if (expression.isFieldName()) {
            if (!schema.hasField(expression.asFieldName()))
                throw new ValidationException("Field " + expression.asFieldName() + " does not exist in schema and is thus not a valid expression");
            return schema.type(expression.asFieldName());
        } else {
            Constant c = expression.asConstant();
            return getConstantType(c);
        }
    }
}
