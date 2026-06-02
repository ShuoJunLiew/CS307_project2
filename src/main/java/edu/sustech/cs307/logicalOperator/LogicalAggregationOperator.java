package edu.sustech.cs307.logicalOperator;

import java.util.Collections;

public class LogicalAggregationOperator extends LogicalOperator {
    private final LogicalOperator child;
    private final String functionName;

    public LogicalAggregationOperator(LogicalOperator child, String functionName) {
        super(Collections.singletonList(child));
        this.child = child;
        this.functionName = functionName;
    }

    public LogicalOperator getChild() {
        return child;
    }

    public String getFunctionName() {
        return functionName;
    }

    @Override
    public String toString() {
        return "LogicalAggregationOperator(func=" + functionName + ")\n└── " + child.toString();
    }
}