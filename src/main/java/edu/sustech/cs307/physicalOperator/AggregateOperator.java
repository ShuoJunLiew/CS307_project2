package edu.sustech.cs307.physicalOperator;

import edu.sustech.cs307.exception.DBException;
import edu.sustech.cs307.exception.ExceptionTypes;
import edu.sustech.cs307.meta.ColumnMeta;
import edu.sustech.cs307.meta.TabCol;
import edu.sustech.cs307.tuple.TempTuple;
import edu.sustech.cs307.tuple.Tuple;
import edu.sustech.cs307.value.Value;
import edu.sustech.cs307.value.ValueComparer;
import edu.sustech.cs307.value.ValueType;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.SelectItem;

import java.util.*;

public class AggregateOperator implements PhysicalOperator {

    private final PhysicalOperator child;
    private final List<Expression> groupByColumns;
    private final List<SelectItem<?>> selectItems;
    private final List<Tuple> resultTuples;
    private final ArrayList<ColumnMeta> outputSchema;
    private final ArrayList<ColumnMeta> childSchema;
    private int currentIndex;
    private boolean isOpen;

    public AggregateOperator(PhysicalOperator child, List<Expression> groupByColumns,
                             List<SelectItem<?>> selectItems) {
        this.child = child;
        this.groupByColumns = groupByColumns != null ? groupByColumns : Collections.emptyList();
        this.selectItems = selectItems;
        this.resultTuples = new ArrayList<>();
        this.outputSchema = new ArrayList<>();
        this.childSchema = new ArrayList<>();
        this.currentIndex = -1;
        this.isOpen = false;
        // Build output schema eagerly since it may be accessed before Begin()
        childSchema.addAll(child.outputSchema());
        try {
            buildOutputSchema();
        } catch (DBException e) {
            // Schema will be empty; error will surface during Begin()
        }
    }

    @Override
    public void Begin() throws DBException {
        child.Begin();
        isOpen = true;
        currentIndex = -1;
        resultTuples.clear();

        // Expand selectItems with AllColumns replaced by individual Columns
        List<SelectItem<?>> expandedItems = expandSelectItems();

        // Read all tuples into groups
        Map<String, List<Tuple>> groups = new LinkedHashMap<>();
        while (child.hasNext()) {
            child.Next();
            Tuple tuple = child.Current();
            if (tuple == null) continue;
            String groupKey = computeGroupKey(tuple);
            groups.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(tuple);
        }

        // For each group, compute aggregate results
        for (List<Tuple> group : groups.values()) {
            List<Value> resultValues = new ArrayList<>();
            TabCol[] tupleSchema = new TabCol[expandedItems.size()];

            for (int i = 0; i < expandedItems.size(); i++) {
                SelectItem<?> item = expandedItems.get(i);
                Expression expr = item.getExpression();

                if (expr instanceof Column col) {
                    String tableName = col.getTable() != null ? col.getTable().getName() : "*";
                    String colName = col.getColumnName();
                    tupleSchema[i] = new TabCol(tableName, colName);
                    resultValues.add(getColumnValue(group.get(0), col));
                } else if (expr instanceof Function func) {
                    String funcName = func.getName().toUpperCase();
                    String displayName = getFunctionDisplayName(func);
                    tupleSchema[i] = new TabCol("*", displayName);

                    switch (funcName) {
                        case "MAX" -> resultValues.add(computeMax(group, func));
                        case "MIN" -> resultValues.add(computeMin(group, func));
                        default -> throw new DBException(
                                ExceptionTypes.NotSupportedOperation(expr));
                    }
                } else {
                    throw new DBException(ExceptionTypes.NotSupportedOperation(expr));
                }
            }
            resultTuples.add(new TempTuple(resultValues, tupleSchema));
        }
    }

    private List<SelectItem<?>> expandSelectItems() {
        List<SelectItem<?>> expanded = new ArrayList<>();
        for (SelectItem<?> item : selectItems) {
            if (item.getExpression() instanceof AllColumns) {
                for (ColumnMeta colMeta : childSchema) {
                    Column col = new Column(colMeta.name);
                    if (colMeta.tableName != null && !colMeta.tableName.equals("*")) {
                        col.setTable(new net.sf.jsqlparser.schema.Table(colMeta.tableName));
                    }
                    expanded.add(new SelectItem<>(col));
                }
            } else {
                expanded.add(item);
            }
        }
        return expanded;
    }

    private void buildOutputSchema() throws DBException {
        for (SelectItem<?> item : selectItems) {
            Expression expr = item.getExpression();
            if (expr instanceof AllColumns) {
                outputSchema.addAll(childSchema);
            } else if (expr instanceof Column col) {
                String colName = col.getColumnName();
                boolean found = false;
                for (ColumnMeta childCol : childSchema) {
                    if (childCol.name.equalsIgnoreCase(colName)) {
                        outputSchema.add(childCol);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    throw new DBException(ExceptionTypes.ColumnDoesNotExist(colName));
                }
            } else if (expr instanceof Function func) {
                String displayName = getFunctionDisplayName(func);
                ValueType type = getFunctionResultType(func);
                int colLen = type == edu.sustech.cs307.value.ValueType.CHAR ? 64 : 8;
                outputSchema.add(new ColumnMeta("*", displayName, type, colLen, 0));
            }
        }
    }

    private ValueType getFunctionResultType(Function func) throws DBException {
        String paramColName = getParamColumnName(func);
        for (ColumnMeta childCol : childSchema) {
            if (childCol.name.equalsIgnoreCase(paramColName)) {
                return childCol.type;
            }
        }
        throw new DBException(ExceptionTypes.ColumnDoesNotExist(paramColName));
    }

    private String getFunctionDisplayName(Function func) {
        return func.getName().toUpperCase() + "(" + getParamColumnName(func) + ")";
    }

    @SuppressWarnings("deprecation")
    private String getParamColumnName(Function func) {
        ExpressionList<?> params = func.getParameters();
        if (params != null && !params.getExpressions().isEmpty()) {
            Expression param = params.getExpressions().get(0);
            if (param instanceof Column col) {
                return col.getColumnName();
            }
        }
        return "*";
    }

    private Value computeMax(List<Tuple> group, Function func) throws DBException {
        String colName = getParamColumnName(func);
        Value maxVal = null;
        for (Tuple tuple : group) {
            Value val = getColumnValueByName(tuple, colName);
            if (val == null) continue;
            if (maxVal == null || ValueComparer.compare(val, maxVal) > 0) {
                maxVal = val;
            }
        }
        return maxVal;
    }

    private Value computeMin(List<Tuple> group, Function func) throws DBException {
        String colName = getParamColumnName(func);
        Value minVal = null;
        for (Tuple tuple : group) {
            Value val = getColumnValueByName(tuple, colName);
            if (val == null) continue;
            if (minVal == null || ValueComparer.compare(val, minVal) < 0) {
                minVal = val;
            }
        }
        return minVal;
    }

    private Value getColumnValue(Tuple tuple, Column col) throws DBException {
        String tableName = col.getTable() != null ? col.getTable().getName() : null;
        if (tableName == null) {
            return getColumnValueByName(tuple, col.getColumnName());
        }
        return tuple.getValue(new TabCol(tableName, col.getColumnName()));
    }

    private Value getColumnValueByName(Tuple tuple, String colName) throws DBException {
        TabCol[] schema = tuple.getTupleSchema();
        if (schema == null) return null;
        for (TabCol tabCol : schema) {
            if (tabCol.getColumnName().equalsIgnoreCase(colName)) {
                return tuple.getValue(tabCol);
            }
        }
        return null;
    }

    private String computeGroupKey(Tuple tuple) throws DBException {
        if (groupByColumns.isEmpty()) return "";
        StringBuilder key = new StringBuilder();
        for (Expression expr : groupByColumns) {
            if (expr instanceof Column col) {
                Value val = getColumnValue(tuple, col);
                if (val != null) {
                    key.append(val.value.toString()).append("|");
                } else {
                    key.append("NULL").append("|");
                }
            }
        }
        return key.toString();
    }

    @Override
    public boolean hasNext() {
        return isOpen && currentIndex + 1 < resultTuples.size();
    }

    @Override
    public void Next() {
        if (hasNext()) {
            currentIndex++;
        }
    }

    @Override
    public Tuple Current() {
        if (currentIndex >= 0 && currentIndex < resultTuples.size()) {
            return resultTuples.get(currentIndex);
        }
        return null;
    }

    @Override
    public void Close() {
        child.Close();
        resultTuples.clear();
        currentIndex = -1;
        isOpen = false;
    }

    @Override
    public ArrayList<ColumnMeta> outputSchema() {
        return outputSchema;
    }
}
