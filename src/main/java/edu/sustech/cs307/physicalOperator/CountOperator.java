package edu.sustech.cs307.physicalOperator;

import edu.sustech.cs307.exception.DBException;
import edu.sustech.cs307.meta.ColumnMeta;
import edu.sustech.cs307.system.DBManager;
import edu.sustech.cs307.tuple.AggregationTuple;
import edu.sustech.cs307.tuple.Tuple;
import edu.sustech.cs307.value.ValueType;
import java.util.ArrayList;

public class CountOperator implements PhysicalOperator {
    private final PhysicalOperator child;
    private final String outputFieldName;
    private Tuple resultTuple = null;
    private boolean hasExecuted = false;

    public CountOperator(PhysicalOperator child, String outputFieldName) {
        this.child = child;
        this.outputFieldName = outputFieldName;
    }

    @Override
    public void Begin() throws DBException {
        child.Begin();
        long recordCount = 0;
        hasExecuted = false;

        // 1. Consume EVERY SINGLE row matching our lower filter criteria
        try {
            while (child.hasNext()) {
                child.Next();
                recordCount++;
            }
        } finally {
            child.Close();
        }

        // 2. Wrap the final calculated total inside our aggregation tuple payload
        this.resultTuple = new AggregationTuple(outputFieldName, recordCount);
    }

    @Override
    public boolean hasNext() throws DBException {
        // Aggregation returns exactly ONE summary row
        return !hasExecuted;
    }

    @Override
    public void Next() throws DBException {
        hasExecuted = true;
    }

    @Override
    public Tuple Current() {
        return this.resultTuple;
    }

    @Override
    public void Close() {
        child.Close();
    }

    @Override
    public ArrayList<ColumnMeta> outputSchema() {
        ArrayList<ColumnMeta> schema = new ArrayList<>();
        schema.add(new ColumnMeta("AGGREGATE", outputFieldName, ValueType.INTEGER, 8, 0));
        return schema;
    }
}