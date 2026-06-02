package edu.sustech.cs307.logicalOperator;

import java.util.Collections;

public class LogicalDeleteOperator extends LogicalOperator {
    private final String tableName;
    private final LogicalOperator child; // This will be a LogicalFilterOperator or LogicalTableScanOperator

    public LogicalDeleteOperator(String tableName, LogicalOperator child) {
        super(Collections.singletonList(child));
        this.tableName = tableName;
        this.child = child;
    }

    public String getTableName() {
        return tableName;
    }

    public LogicalOperator getChild() {
        return child;
    }

    @Override
    public String toString() {
        return "LogicalDeleteOperator(table=" + tableName + ")\n└── " + child.toString();
    }
}