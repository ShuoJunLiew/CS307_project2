package edu.sustech.cs307.logicalOperator;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.statement.select.SelectItem;

import java.util.Collections;
import java.util.List;

public class LogicalAggregateOperator extends LogicalOperator {

    private final LogicalOperator child;
    private final List<Expression> groupByColumns;
    private final List<SelectItem<?>> selectItems;

    public LogicalAggregateOperator(LogicalOperator child, List<Expression> groupByColumns,
                                    List<SelectItem<?>> selectItems) {
        super(Collections.singletonList(child));
        this.child = child;
        this.groupByColumns = groupByColumns;
        this.selectItems = selectItems;
    }

    public LogicalOperator getChild() {
        return child;
    }

    public List<Expression> getGroupByColumns() {
        return groupByColumns;
    }

    public List<SelectItem<?>> getSelectItems() {
        return selectItems;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("LogicalAggregateOperator(groupBy=").append(groupByColumns)
                .append(", selectItems=").append(selectItems).append(")");
        String[] childLines = child.toString().split("\\R");
        if (childLines.length > 0) {
            sb.append("\n    └── ").append(childLines[0]);
            for (int i = 1; i < childLines.length; i++) {
                sb.append("\n    ").append(childLines[i]);
            }
        }
        return sb.toString();
    }
}
