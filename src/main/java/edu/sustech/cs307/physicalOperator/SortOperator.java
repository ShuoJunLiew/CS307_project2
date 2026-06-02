package edu.sustech.cs307.physicalOperator;

import edu.sustech.cs307.exception.DBException;
import edu.sustech.cs307.meta.ColumnMeta;
import edu.sustech.cs307.meta.TabCol;
import edu.sustech.cs307.tuple.Tuple;
import edu.sustech.cs307.value.Value;
import edu.sustech.cs307.value.ValueComparer;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.OrderByElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class SortOperator implements PhysicalOperator {

    private final PhysicalOperator child;
    private final List<OrderByElement> orderByElements;
    private final List<Tuple> sortedTuples;
    private int currentIndex;
    private boolean isOpen;

    public SortOperator(PhysicalOperator child, List<OrderByElement> orderByElements) {
        this.child = child;
        this.orderByElements = orderByElements != null ? orderByElements : Collections.emptyList();
        this.sortedTuples = new ArrayList<>();
        this.currentIndex = -1;
        this.isOpen = false;
    }

    @Override
    public void Begin() throws DBException {
        child.Begin();
        isOpen = true;
        currentIndex = -1;
        sortedTuples.clear();

        // Read all tuples from child
        while (child.hasNext()) {
            child.Next();
            Tuple tuple = child.Current();
            if (tuple != null) {
                sortedTuples.add(tuple);
            }
        }

        // Sort tuples
        if (!orderByElements.isEmpty()) {
            sortedTuples.sort(new OrderByComparator());
        }
    }

    private class OrderByComparator implements Comparator<Tuple> {
        @Override
        public int compare(Tuple t1, Tuple t2) {
            try {
                for (OrderByElement obe : orderByElements) {
                    Expression expr = obe.getExpression();
                    Value v1 = resolveValue(t1, expr);
                    Value v2 = resolveValue(t2, expr);

                    int cmp = ValueComparer.compare(v1, v2);
                    if (cmp != 0) {
                        return obe.isAsc() ? cmp : -cmp;
                    }
                }
                return 0;
            } catch (DBException e) {
                throw new RuntimeException("Sort comparison failed: " + e.getMessage(), e);
            }
        }
    }

    private Value resolveValue(Tuple tuple, Expression expr) throws DBException {
        if (expr instanceof Column col) {
            String tableName = col.getTable() != null ? col.getTable().getName() : null;
            if (tableName == null) {
                return getColumnValueByName(tuple, col.getColumnName());
            }
            return tuple.getValue(new TabCol(tableName, col.getColumnName()));
        }
        if (expr instanceof Function func) {
            // ORDER BY MAX(salary) after GROUP BY: resolve by function display name
            String displayName = func.getName().toUpperCase() + "("
                    + getParamColumnName(func) + ")";
            return getColumnValueByName(tuple, displayName);
        }
        // Fall back to generic expression evaluation
        return tuple.evaluateExpression(expr);
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

    @Override
    public boolean hasNext() {
        return isOpen && currentIndex + 1 < sortedTuples.size();
    }

    @Override
    public void Next() {
        if (hasNext()) {
            currentIndex++;
        }
    }

    @Override
    public Tuple Current() {
        if (currentIndex >= 0 && currentIndex < sortedTuples.size()) {
            return sortedTuples.get(currentIndex);
        }
        return null;
    }

    @Override
    public void Close() {
        child.Close();
        sortedTuples.clear();
        currentIndex = -1;
        isOpen = false;
    }

    @Override
    public ArrayList<ColumnMeta> outputSchema() {
        return child.outputSchema();
    }
}
