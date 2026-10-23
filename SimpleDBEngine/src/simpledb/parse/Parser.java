package simpledb.parse;

import java.util.*;

import simpledb.materialize.*;
import simpledb.query.*;
import simpledb.record.*;

/**
 * The SimpleDB parser.
 *
 * @author Edward Sciore
 */
public class Parser {
    private final Lexer lex;

    public Parser(String s) {
        lex = new Lexer(s);
    }

    public static void handleBadSyntaxException(BadSyntaxException e) {
        System.out.println("Syntax error: " + e.getMessage());
    }

// Methods for parsing predicates, terms, expressions, constants, and fields

    public String field() {
        return lex.eatId();
    }

    public Constant constant() {
        if (lex.matchStringConstant())
            return new Constant(lex.eatStringConstant());
        else
            return new Constant(lex.eatIntConstant());
    }

    public Expression expression() {
        if (lex.matchId())
            return new Expression(field());
        else
            return new Expression(constant());
    }

    public Term term() {
        Expression lhs = expression();
        String opr = lex.eatCompOperator();
        Expression rhs = expression();
        return new Term(lhs, rhs, opr);
    }

    public Predicate predicate() {
        Predicate pred = new Predicate(term());
        if (lex.matchKeyword("and")) {
            lex.eatKeyword("and");
            pred.conjoinWith(predicate());
        }
        return pred;
    }

    public SortField sortField() {
        // an aggregate such as count(sid) sorts on its output field countofsid
        String field = matchAggregationFn() ? aggregationFn().fieldName() : field();
        if (lex.matchKeyword("asc")) {
            lex.eatKeyword("asc");
            return new SortField(field, true);
        } else if (lex.matchKeyword("desc")) {
            lex.eatKeyword("desc");
            return new SortField(field, false);
        } else {
            return new SortField(field);
        }
    }

    private static final List<String> AGGREGATES = Arrays.asList("sum", "count", "avg", "min", "max");

    private boolean matchAggregationFn() {
        for (String agg : AGGREGATES)
            if (lex.matchKeyword(agg))
                return true;
        return false;
    }

    public AggregationFn aggregationFn() {
        String agg = null;
        for (String a : AGGREGATES)
            if (lex.matchKeyword(a))
                agg = a;
        if (agg == null)
            throw new BadSyntaxException("expected an aggregate function", lex.getQuery(), lex.getCurrentPosition());
        lex.eatKeyword(agg);
        lex.eatDelim('(');
        String fldname = field();
        lex.eatDelim(')');
        return switch (agg) {
            case "sum" -> new SumFn(fldname);
            case "count" -> new CountFn(fldname);
            case "avg" -> new AvgFn(fldname);
            case "min" -> new MinFn(fldname);
            default -> new MaxFn(fldname);
        };
    }

// Methods for parsing queries

    public QueryData query() {
        lex.eatKeyword("select");
        boolean isDistinct = false;
        if (lex.matchKeyword("distinct")) {
            lex.eatKeyword("distinct");
            isDistinct = true;
        }
        List<String> fields = new ArrayList<>();
        List<AggregationFn> aggfns = new ArrayList<>();
        selectList(fields, aggfns);
        lex.eatKeyword("from");
        Collection<String> tables = tableList();
        Predicate pred = new Predicate();
        if (lex.matchKeyword("where")) {
            lex.eatKeyword("where");
            pred = predicate();
        }
        List<SortField> groupFields = new ArrayList<>();
        if (lex.matchKeyword("group")) {
            lex.eatKeyword("group");
            lex.eatKeyword("by");
            groupFields = groupList();
        }
        List<SortField> sortFields = new ArrayList<>();
        if (lex.matchKeyword("order")) {
            lex.eatKeyword("order");
            lex.eatKeyword("by");
            sortFields = sortList();
        }
        return new QueryData(fields, tables, pred, groupFields, aggfns, sortFields, isDistinct);
    }

    /**
     * Parses the select list. Aggregates are added to aggfns,
     * and their output field names are added to fields.
     */
    private void selectList(List<String> fields, List<AggregationFn> aggfns) {
        if (matchAggregationFn()) {
            AggregationFn fn = aggregationFn();
            aggfns.add(fn);
            fields.add(fn.fieldName());
        } else {
            fields.add(field());
        }
        if (lex.matchDelim(',')) {
            lex.eatDelim(',');
            selectList(fields, aggfns);
        }
    }

    private List<SortField> groupList() {
        List<SortField> L = new ArrayList<>();
        L.add(new SortField(field()));
        if (lex.matchDelim(',')) {
            lex.eatDelim(',');
            L.addAll(groupList());
        }
        return L;
    }

    private Collection<String> tableList() {
        Collection<String> L = new ArrayList<>();
        L.add(lex.eatId());
        if (lex.matchDelim(',')) {
            lex.eatDelim(',');
            L.addAll(tableList());
        }
        return L;
    }

    private List<SortField> sortList() {
        List<SortField> L = new ArrayList<>();
        L.add(sortField());
        if (lex.matchDelim(',')) {
            lex.eatDelim(',');
            L.addAll(sortList());
        }
        return L;
    }

// Methods for parsing the various update commands

    public Object updateCmd() {
        if (lex.matchKeyword("insert"))
            return insert();
        else if (lex.matchKeyword("delete"))
            return delete();
        else if (lex.matchKeyword("update"))
            return modify();
        else if (lex.matchKeyword("create"))
            return create();
        else
            throw new BadSyntaxException("expected SQL to start with select, insert, delete, update, or create", lex.getQuery(), lex.getCurrentPosition());
    }

    private Object create() {
        lex.eatKeyword("create");
        if (lex.matchKeyword("table"))
            return createTable();
        else if (lex.matchKeyword("view"))
            return createView();
        else
            return createIndex();
    }

// Method for parsing delete commands

    public DeleteData delete() {
        lex.eatKeyword("delete");
        lex.eatKeyword("from");
        String tblname = lex.eatId();
        Predicate pred = new Predicate();
        if (lex.matchKeyword("where")) {
            lex.eatKeyword("where");
            pred = predicate();
        }
        return new DeleteData(tblname, pred);
    }

// Methods for parsing insert commands

    public InsertData insert() {
        lex.eatKeyword("insert");
        lex.eatKeyword("into");
        String tblname = lex.eatId();
        lex.eatDelim('(');
        List<String> flds = fieldList();
        lex.eatDelim(')');
        lex.eatKeyword("values");
        lex.eatDelim('(');
        List<Constant> vals = constList();
        lex.eatDelim(')');
        return new InsertData(tblname, flds, vals);
    }

    private List<String> fieldList() {
        List<String> L = new ArrayList<>();
        L.add(field());
        if (lex.matchDelim(',')) {
            lex.eatDelim(',');
            L.addAll(fieldList());
        }
        return L;
    }

    private List<Constant> constList() {
        List<Constant> L = new ArrayList<>();
        L.add(constant());
        if (lex.matchDelim(',')) {
            lex.eatDelim(',');
            L.addAll(constList());
        }
        return L;
    }

// Method for parsing modify commands

    public ModifyData modify() {
        lex.eatKeyword("update");
        String tblname = lex.eatId();
        lex.eatKeyword("set");
        String fldname = field();
        lex.eatDelim('=');
        Expression newval = expression();
        Predicate pred = new Predicate();
        if (lex.matchKeyword("where")) {
            lex.eatKeyword("where");
            pred = predicate();
        }
        return new ModifyData(tblname, fldname, newval, pred);
    }

// Method for parsing create table commands

    public CreateTableData createTable() {
        lex.eatKeyword("table");
        String tblname = lex.eatId();
        lex.eatDelim('(');
        Schema sch = fieldDefs();
        lex.eatDelim(')');
        return new CreateTableData(tblname, sch);
    }

    private Schema fieldDefs() {
        Schema schema = fieldDef();
        if (lex.matchDelim(',')) {
            lex.eatDelim(',');
            Schema schema2 = fieldDefs();
            schema.addAll(schema2);
        }
        return schema;
    }

    private Schema fieldDef() {
        String fldname = field();
        return fieldType(fldname);
    }

    private Schema fieldType(String fldname) {
        Schema schema = new Schema();
        if (lex.matchKeyword("int")) {
            lex.eatKeyword("int");
            schema.addIntField(fldname);
        } else {
            lex.eatKeyword("varchar");
            lex.eatDelim('(');
            int strLen = lex.eatIntConstant();
            lex.eatDelim(')');
            schema.addStringField(fldname, strLen);
        }
        return schema;
    }

// Method for parsing create view commands

    public CreateViewData createView() {
        lex.eatKeyword("view");
        String viewname = lex.eatId();
        lex.eatKeyword("as");
        QueryData qd = query();
        return new CreateViewData(viewname, qd);
    }


//  Method for parsing create index commands

    public CreateIndexData createIndex() {
        lex.eatKeyword("index");
        String idxname = lex.eatId();
        lex.eatKeyword("on");
        String tblname = lex.eatId();
        lex.eatDelim('(');
        String fldname = field();
        lex.eatDelim(')');
        String idxtype = "hash";
        if (lex.matchKeyword("using")) {
            lex.eatKeyword("using");
            if (lex.matchKeyword("hash")) {
                lex.eatKeyword("hash");
                idxtype = "hash";
            } else if (lex.matchKeyword("btree")) {
                lex.eatKeyword("btree");
                idxtype = "btree";
            } else
                throw new BadSyntaxException("expected keyword hash or btree for index type", lex.getQuery(), lex.getCurrentPosition());
        }
        return new CreateIndexData(idxname, tblname, fldname, idxtype);
    }
}

