package edu.sustech.cs307.logicalOperator;

import java.util.Collections;

/**
 * Logical operator representing an index-based table scan.
 * When a table has an index defined, the planner uses this operator
 * instead of a full table scan for potentially more efficient access.
 */
public class LogicalIndexScanOperator extends LogicalOperator {

    private final String tableName;

    public LogicalIndexScanOperator(String tableName) {
        super(Collections.emptyList());
        this.tableName = tableName;
    }

    public String getTableName() {
        return tableName;
    }

    @Override
    public String toString() {
        return "LogicalIndexScanOperator(table=" + tableName + ")";
    }
}
